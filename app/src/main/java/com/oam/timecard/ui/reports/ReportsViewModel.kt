package com.oam.timecard.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oam.timecard.data.db.toDomain
import com.oam.timecard.data.db.toJobInfo
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.data.settings.SettingsRepository
import com.oam.timecard.domain.engine.HoursCalculator
import com.oam.timecard.domain.engine.PayPeriod
import com.oam.timecard.domain.engine.PayPeriodConfig
import com.oam.timecard.domain.engine.PayPeriods
import com.oam.timecard.domain.engine.TimesheetBuilder
import com.oam.timecard.domain.model.JobPeriodSummary
import com.oam.timecard.ui.JobLabel
import com.oam.timecard.ui.toLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JobReport(val job: JobLabel, val summary: JobPeriodSummary, val scheduled: Duration?)

/** Pay totals can only be added up within one currency. */
data class CurrencyTotal(val currency: String, val gross: BigDecimal)

data class ReportsUiState(
    val loading: Boolean = true,
    val period: PayPeriod? = null,
    val config: PayPeriodConfig? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val isCurrent: Boolean = true,
    val jobs: List<JobReport> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
)

private data class PeriodKey(val period: PayPeriod, val config: PayPeriodConfig, val weekStart: DayOfWeek, val offset: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repo: TimeCardRepository,
    private val settingsRepo: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val offset = MutableStateFlow(0)
    private val ticker = flow { while (true) { emit(clock.instant()); delay(30_000) } }

    private fun today() = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    private val key = combine(settingsRepo.settings, offset) { s, off ->
        var p = PayPeriods.containing(today(), s.payPeriod)
        repeat(kotlin.math.abs(off)) { p = if (off < 0) PayPeriods.previous(p, s.payPeriod) else PayPeriods.next(p, s.payPeriod) }
        PeriodKey(p, s.payPeriod, s.weekStart, off)
    }.distinctUntilChanged()

    val state: StateFlow<ReportsUiState> = key.flatMapLatest { k ->
        combine(
            repo.observeShifts(HoursCalculator.loadFrom(k.period.start, k.weekStart), k.period.endInclusive),
            repo.observeMarks(k.period.start, k.period.endInclusive),
            repo.activeJobs,
            ticker,
        ) { shifts, marks, jobs, now ->
            val ts = TimesheetBuilder.build(
                jobs = jobs.map { it.toJobInfo() },
                shifts = shifts.map { it.toDomain() },
                marks = marks.map { it.toDomain() },
                weekStart = k.weekStart,
                from = k.period.start,
                to = k.period.endInclusive,
                now = now,
            )
            val days = java.time.temporal.ChronoUnit.DAYS.between(k.period.start, k.period.endInclusive) + 1
            val reports = jobs.mapIndexed { i, j ->
                val label = j.toLabel()
                JobReport(label, ts.perJob[i], label.scheduledPerWeek?.let { Duration.ofSeconds(it.seconds * days / 7) })
            }
            ReportsUiState(
                loading = false,
                period = k.period,
                config = k.config,
                weekStart = k.weekStart,
                isCurrent = k.offset == 0,
                jobs = reports,
                totals = reports.groupBy { it.job.currency }.map { (cur, list) ->
                    CurrencyTotal(cur, list.fold(BigDecimal.ZERO) { a, r -> a + r.summary.grossPay })
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    fun previous() { offset.value -= 1 }
    fun next() { offset.value += 1 }
    fun current() { offset.value = 0 }

    fun savePeriodSettings(config: PayPeriodConfig, weekStart: DayOfWeek) {
        viewModelScope.launch {
            settingsRepo.setPayPeriod(config)
            settingsRepo.setWeekStart(weekStart)
            offset.value = 0
        }
    }
}
