package com.oamsapps.punchd.ui.timesheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oamsapps.punchd.data.db.toDomain
import com.oamsapps.punchd.data.db.toJobInfo
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.Timesheet
import com.oamsapps.punchd.domain.engine.TimesheetBuilder
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.toLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import com.oamsapps.punchd.util.retryable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TimesheetMode { WEEK, MONTH }

data class TimesheetUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val mode: TimesheetMode = TimesheetMode.WEEK,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val jobs: List<JobLabel> = emptyList(),
    val timesheet: Timesheet? = null,
) {
    fun job(id: Long) = jobs.firstOrNull { it.id == id }
}

private data class Range(val mode: TimesheetMode, val from: LocalDate, val to: LocalDate, val weekStart: DayOfWeek)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimesheetViewModel @Inject constructor(
    private val repo: TimeCardRepository,
    settingsRepo: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private fun today() = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    private val mode = MutableStateFlow(TimesheetMode.WEEK)
    private val anchor = MutableStateFlow(today())

    /** Keeps a running shift's hours moving without re-querying the database. */
    private val ticker = flow { while (true) { emit(clock.instant()); delay(30_000) } }

    private val range = combine(mode, anchor, settingsRepo.settings.map { it.weekStart }) { m, a, ws ->
        when (m) {
            TimesheetMode.WEEK -> HoursCalculator.weekStartOf(a, ws).let { Range(m, it, it.plusDays(6), ws) }
            TimesheetMode.MONTH -> Range(m, a.withDayOfMonth(1), a.with(TemporalAdjusters.lastDayOfMonth()), ws)
        }
    }.distinctUntilChanged()

    private val reloads = MutableStateFlow(0)

    /** Day-mark saves that failed, for the snackbar's Retry. */
    private val _saveFailed = Channel<() -> Unit>(Channel.CONFLATED)
    val saveFailed = _saveFailed.receiveAsFlow()

    val state: StateFlow<TimesheetUiState> = retryable(reloads, { TimesheetUiState(loading = false, loadError = true) }) {
      range.flatMapLatest { r ->
        combine(
            repo.observeShifts(HoursCalculator.loadFrom(r.from, r.weekStart), r.to),
            repo.observeMarks(r.from, r.to),
            repo.activeJobs,
            ticker,
        ) { shifts, marks, jobs, now ->
            TimesheetUiState(
                loading = false,
                mode = r.mode,
                from = r.from,
                to = r.to,
                today = today(),
                jobs = jobs.map { it.toLabel() },
                timesheet = TimesheetBuilder.build(
                    jobs = jobs.map { it.toJobInfo() },
                    shifts = shifts.map { it.toDomain() },
                    marks = marks.map { it.toDomain() },
                    weekStart = r.weekStart,
                    from = r.from,
                    to = r.to,
                    now = now,
                ),
            )
        }
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimesheetUiState())

    fun reload() { reloads.value++ }

    fun setMode(m: TimesheetMode) { mode.value = m }

    fun previous() = anchor.step(-1)
    fun next() = anchor.step(1)
    fun goToToday() { anchor.value = today() }

    private fun MutableStateFlow<LocalDate>.step(by: Long) {
        value = if (mode.value == TimesheetMode.WEEK) value.plusWeeks(by) else value.plusMonths(by)
    }

    fun setDayMark(date: LocalDate, jobId: Long, type: DayMarkType?, paid: Duration) {
        viewModelScope.launch {
            try {
                repo.setDayMark(date, jobId, type, paid.toMinutes().toInt())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveFailed.trySend { setDayMark(date, jobId, type, paid) }
            }
        }
    }
}
