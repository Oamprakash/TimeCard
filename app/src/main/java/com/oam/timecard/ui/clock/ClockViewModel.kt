package com.oam.timecard.ui.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oam.timecard.clock.ClockController
import com.oam.timecard.data.db.JobWithRules
import com.oam.timecard.data.db.ShiftWithBreaks
import com.oam.timecard.data.db.toDomain
import com.oam.timecard.data.repository.JobLimitReachedException
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.data.settings.AppSettings
import com.oam.timecard.data.settings.SettingsRepository
import com.oam.timecard.domain.engine.HoursCalculator
import com.oam.timecard.domain.engine.RunningSnapshot
import com.oam.timecard.domain.engine.RunningState
import com.oam.timecard.domain.model.DayFlag
import com.oam.timecard.domain.model.PresetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JobUi(
    val id: Long,
    val name: String,
    val color: Int,
    val currency: String,
    val weeklyOtAfter: Duration?,
)

data class ClockUiState(
    val loading: Boolean = true,
    val jobs: List<JobUi> = emptyList(),
    val selectedJobId: Long? = null,
    val running: RunningSnapshot? = null,
    val today: Duration = Duration.ZERO,
    val todayFlags: Set<DayFlag> = emptySet(),
    val week: Duration = Duration.ZERO,
    val weekOvertime: Duration = Duration.ZERO,
    val weeklyThreshold: Duration? = null,
    val weekPay: BigDecimal = BigDecimal.ZERO,
    val currency: String = "USD",
) {
    val selectedJob: JobUi? get() = jobs.firstOrNull { it.id == selectedJobId }
}

enum class ClockMessage { JOB_LIMIT, INVALID_JOB }

private data class WeekWindow(val from: LocalDate, val to: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClockViewModel @Inject constructor(
    private val repo: TimeCardRepository,
    private val settingsRepo: SettingsRepository,
    private val controller: ClockController,
    private val clock: Clock,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    /** True while a punch is being written; the button ignores taps so one tap = one punch. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<ClockMessage>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages = _messages.asSharedFlow()

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(1_000)
        }
    }

    private val settings = settingsRepo.settings

    /** Shifts of the current workweek (all jobs). Re-queries only when the week changes. */
    private val weekShifts: Flow<Pair<WeekWindow, List<ShiftWithBreaks>>> =
        combine(settings, ticker) { s, now ->
            val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
            val from = HoursCalculator.weekStartOf(today, s.weekStart)
            WeekWindow(from, from.plusDays(6))
        }
            .distinctUntilChanged()
            .flatMapLatest { w -> repo.observeShifts(w.from, w.to).map { w to it } }

    val state: StateFlow<ClockUiState> = combine(
        repo.activeJobs, repo.runningShift, settings, weekShifts, ticker,
    ) { jobs, running, s, (window, shifts), now ->
        build(jobs, running, s, window, shifts, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClockUiState())

    init {
        // Covers a notification the user swiped away (Android 14+ allows that).
        viewModelScope.launch { controller.sync() }
    }

    private fun build(
        jobs: List<JobWithRules>,
        running: ShiftWithBreaks?,
        s: AppSettings,
        window: WeekWindow,
        shifts: List<ShiftWithBreaks>,
        now: Instant,
    ): ClockUiState {
        val jobUis = jobs.map {
            JobUi(it.job.id, it.job.name, it.job.color, it.job.currency, it.rules.toDomain().weeklyOtAfter)
        }
        val selectedId = running?.shift?.jobId
            ?: s.selectedJobId?.takeIf { id -> jobUis.any { it.id == id } }
            ?: jobUis.firstOrNull()?.id
        val job = jobs.firstOrNull { it.job.id == selectedId }
            ?: return ClockUiState(loading = false, jobs = jobUis, selectedJobId = selectedId)

        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val rules = job.rules.toDomain()
        val summary = HoursCalculator.summarize(
            jobId = job.job.id,
            shifts = shifts.map { it.toDomain() },
            marks = emptyList(), // day marks arrive with the Timesheet in Sprint 3
            rules = rules,
            hourlyRate = job.job.hourlyRate,
            weekStart = s.weekStart,
            start = window.from,
            endInclusive = minOf(today, window.to),
            now = now,
        )
        val todaySummary = summary.days.lastOrNull { it.date == today }

        return ClockUiState(
            loading = false,
            jobs = jobUis,
            selectedJobId = selectedId,
            running = running?.let { RunningState.snapshot(it.toDomain(), now) },
            today = todaySummary?.worked ?: Duration.ZERO,
            todayFlags = todaySummary?.flags.orEmpty(),
            week = summary.worked,
            weekOvertime = summary.overtime + summary.doubleTime,
            weeklyThreshold = rules.weeklyOtAfter,
            weekPay = summary.grossPay,
            currency = job.job.currency,
        )
    }

    fun selectJob(jobId: Long) {
        if (state.value.running != null) return // can't switch job mid-shift
        viewModelScope.launch { settingsRepo.setSelectedJob(jobId) }
    }

    fun clockIn() = punch {
        val jobId = state.value.selectedJobId ?: return@punch
        controller.clockIn(jobId)
    }

    fun clockOut() = punch { controller.clockOut() }

    fun toggleBreak() = punch {
        if (state.value.running?.onBreak == true) controller.endBreak() else controller.startBreak()
    }

    /** Called after the notification permission dialog; posts the notification if it was granted. */
    fun onNotificationPermissionResult() {
        viewModelScope.launch { controller.sync() }
    }

    fun addJob(name: String, rateText: String, currency: String, preset: PresetKind, color: Int) {
        val rate = rateText.trim().replace(',', '.').toBigDecimalOrNull()
        if (name.isBlank() || rate == null || rate.signum() < 0) {
            _messages.tryEmit(ClockMessage.INVALID_JOB)
            return
        }
        viewModelScope.launch {
            val s = settings.first()
            try {
                val id = repo.addJob(name.trim(), color, rate, currency, preset, unlimitedJobs = s.removeAds)
                settingsRepo.setSelectedJob(id)
            } catch (_: JobLimitReachedException) {
                _messages.tryEmit(ClockMessage.JOB_LIMIT)
            }
        }
    }

    private fun punch(block: suspend () -> Unit) {
        if (!_busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try { block() } finally { _busy.value = false }
        }
    }
}
