package com.oamsapps.punchd.ui.shift

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oamsapps.punchd.ads.InterstitialManager
import com.oamsapps.punchd.clock.ClockController
import com.oamsapps.punchd.data.repository.SaveShiftResult
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.BreakDraft
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.ShiftDraft
import com.oamsapps.punchd.domain.engine.ShiftDrafts
import com.oamsapps.punchd.domain.engine.ShiftError
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.toLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShiftEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** Editing the shift that is clocked in right now: end and breaks stay under the Clock tab. */
    val isRunning: Boolean = false,
    val jobs: List<JobLabel> = emptyList(),
    val jobId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(17, 0),
    val breaks: List<BreakDraft> = emptyList(),
    val runningBreakSince: LocalTime? = null,
    val note: String = "",
    val errors: Set<ShiftError> = emptySet(),
    val saving: Boolean = false,
    /** Writing failed (not a validation problem); the screen offers to try again. */
    val saveFailed: Boolean = false,
) {
    /** End on the clock at or before start = next day (night shift). */
    val endsNextDay: Boolean get() = !isRunning && !end.isAfter(start)
}

@HiltViewModel
class ShiftEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TimeCardRepository,
    private val settings: SettingsRepository,
    private val controller: ClockController,
    private val clock: Clock,
    private val interstitial: InterstitialManager,
) : ViewModel() {

    companion object {
        const val ARG_ID = "id"
        const val ARG_DATE = "date"
    }

    private val shiftId: Long? = savedState.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private val initialDate: LocalDate? = savedState.get<String>(ARG_DATE)?.let(LocalDate::parse)

    private val _state = MutableStateFlow(ShiftEditUiState())
    val state: StateFlow<ShiftEditUiState> = _state.asStateFlow()

    /** One-shot: the screen closes when this emits; true = a manual edit was saved. */
    private val _done = Channel<Boolean>(Channel.CONFLATED)
    val done = _done.receiveAsFlow()

    /** Capped (1 per 5 min, 2 per day) and only after saving a manual edit. Returns when the ad closes. */
    suspend fun showInterstitialAfterSave(activity: android.app.Activity) = interstitial.maybeShow(activity)

    init {
        interstitial.preload()
        viewModelScope.launch {
            val jobs = repo.activeJobs.first().map { it.toLabel() }
            val existing = shiftId?.let { repo.getShift(it) }
            _state.value = if (existing != null) {
                val z = existing.shift.zoneId
                fun local(i: java.time.Instant) = i.atZone(z).toLocalTime().withSecond(0).withNano(0)
                ShiftEditUiState(
                    loading = false,
                    isNew = false,
                    isRunning = existing.shift.endUtc == null,
                    jobs = jobs,
                    jobId = existing.shift.jobId,
                    date = existing.shift.workDate,
                    start = local(existing.shift.startUtc),
                    end = existing.shift.endUtc?.let(::local) ?: LocalTime.of(17, 0),
                    breaks = existing.breaks.filter { it.endUtc != null }.map { BreakDraft(local(it.startUtc), local(it.endUtc!!)) },
                    runningBreakSince = existing.breaks.firstOrNull { it.endUtc == null }?.let { local(it.startUtc) },
                    note = existing.shift.note.orEmpty(),
                )
            } else {
                val selected = settings.settings.first().selectedJobId
                ShiftEditUiState(
                    loading = false,
                    jobs = jobs,
                    jobId = selected?.takeIf { id -> jobs.any { it.id == id } } ?: jobs.firstOrNull()?.id,
                    date = initialDate ?: today(),
                )
            }
        }
    }

    private fun today() = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    fun setJob(id: Long) = edit { copy(jobId = id) }
    fun setDate(d: LocalDate) = edit { copy(date = d) }
    fun setStart(t: LocalTime) = edit { copy(start = t) }
    fun setEnd(t: LocalTime) = edit { copy(end = t) }
    fun setNote(n: String) = edit { copy(note = n.take(500)) }
    fun removeBreak(i: Int) = edit { copy(breaks = breaks.filterIndexed { idx, _ -> idx != i }) }
    fun setBreak(i: Int, b: BreakDraft) = edit { copy(breaks = breaks.mapIndexed { idx, old -> if (idx == i) b else old }) }

    /** Adds a 30-minute break in the middle of the shift; the user can then adjust it. */
    fun addBreak() = edit {
        val s = date.atTime(start)
        var e = date.atTime(end)
        if (!e.isAfter(s)) e = e.plusDays(1)
        val mid = s.plus(Duration.between(s, e).dividedBy(2)).withSecond(0).withNano(0)
        val bStart = mid.minusMinutes(15)
        copy(breaks = breaks + BreakDraft(bStart.toLocalTime(), bStart.plusMinutes(30).toLocalTime()))
    }

    /** Worked time preview (breaks excluded); null while there is nothing to show. */
    fun preview(s: ShiftEditUiState): Duration? {
        if (s.isRunning || s.jobId == null) return null
        val r = ShiftDrafts.resolve(s.toDraft(), ZoneId.systemDefault())
        val end = r.end ?: return null
        val gross = Duration.between(r.start, end)
        val breaks = HoursCalculator.breakTotal(
            r.breaks.map { com.oamsapps.punchd.domain.model.BreakSpan(it.start, it.end) }, r.start, end, end,
        )
        return gross.minus(breaks).takeIf { !it.isNegative }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.jobId == null) return
        _state.update { it.copy(saving = true, errors = emptySet(), saveFailed = false) }
        viewModelScope.launch {
            try {
                when (val result = repo.saveShift(s.toDraft())) {
                    is SaveShiftResult.Saved -> {
                        runCatching { controller.sync() } // editing the running shift's start moves the notification timer
                        _done.trySend(true)
                    }
                    is SaveShiftResult.Invalid -> _state.update { it.copy(saving = false, errors = result.errors) }
                    SaveShiftResult.NotFound -> _done.trySend(false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) } // the typed values stay; Save again retries
            }
        }
    }

    fun delete() {
        val id = shiftId ?: return
        viewModelScope.launch {
            try {
                repo.deleteShift(id)
                runCatching { controller.sync() } // deleting the running shift removes the notification
                _done.trySend(false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saveFailed = true) }
            }
        }
    }

    private fun ShiftEditUiState.toDraft() = ShiftDraft(
        id = shiftId,
        jobId = jobId ?: 0,
        date = date,
        start = start,
        end = if (isRunning) null else end,
        breaks = breaks,
        note = note,
    )

    private inline fun edit(crossinline f: ShiftEditUiState.() -> ShiftEditUiState) =
        _state.update { it.f().copy(errors = emptySet(), saveFailed = false) }
}
