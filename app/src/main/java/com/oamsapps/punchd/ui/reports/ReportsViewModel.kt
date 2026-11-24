package com.oamsapps.punchd.ui.reports

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oamsapps.punchd.ads.AdsGate
import com.oamsapps.punchd.ads.RewardResult
import com.oamsapps.punchd.ads.RewardedManager
import com.oamsapps.punchd.billing.BillingManager
import com.oamsapps.punchd.data.db.toDomain
import com.oamsapps.punchd.export.ExportFormat
import com.oamsapps.punchd.export.TimesheetExporter
import com.oamsapps.punchd.data.db.toJobInfo
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.PayPeriod
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.domain.engine.PayPeriods
import com.oamsapps.punchd.domain.engine.TimesheetBuilder
import com.oamsapps.punchd.domain.model.JobPeriodSummary
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.toLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import com.oamsapps.punchd.util.retryable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

sealed interface ExportEvent {
    data class Share(val intent: Intent) : ExportEvent
    /** Retry exports again without another ad: the user already earned it. */
    data class Failed(val format: ExportFormat) : ExportEvent
}

data class JobReport(val job: JobLabel, val summary: JobPeriodSummary, val scheduled: Duration?) {
    /** Worked + paid leave: what the report is about. */
    val hours: Duration get() = summary.worked.plus(summary.paidLeave)
    val hasHours: Boolean get() = !hours.isZero
}

/** Pay totals can only be added up within one currency. */
data class CurrencyTotal(val currency: String, val gross: BigDecimal)

data class ReportsUiState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val period: PayPeriod? = null,
    val config: PayPeriodConfig? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val isCurrent: Boolean = true,
    /** Shown jobs: ones with hours first (most hours on top), then zero-hour jobs. */
    val jobs: List<JobReport> = emptyList(),
    val totals: List<CurrencyTotal> = emptyList(),
    /** Combined worked + paid leave and overtime across the shown jobs. */
    val totalHours: Duration = Duration.ZERO,
    val totalOvertime: Duration = Duration.ZERO,
    /** All active jobs, for the "All jobs ⌄" filter. */
    val jobOptions: List<JobLabel> = emptyList(),
    /** Null = all jobs. */
    val selectedJobId: Long? = null,
) {
    val selectedJob: JobLabel? get() = jobOptions.firstOrNull { it.id == selectedJobId }
}

private data class PeriodKey(val period: PayPeriod, val config: PayPeriodConfig, val weekStart: DayOfWeek, val offset: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repo: TimeCardRepository,
    private val settingsRepo: SettingsRepository,
    private val clock: Clock,
    private val exporter: TimesheetExporter,
    private val gate: AdsGate,
    private val rewarded: RewardedManager,
    private val billing: BillingManager,
) : ViewModel() {

    private val _exporting = MutableStateFlow<ExportFormat?>(null)
    /** Format being written right now (buttons show progress, extra taps are ignored). */
    val exporting: StateFlow<ExportFormat?> = _exporting.asStateFlow()

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        rewarded.preload()
        billing.refresh()
    }

    /** PDF and Excel cost one rewarded ad unless ads are off (Remove ads bought, or no consent). CSV is always free. */
    fun needsRewardedAd(format: ExportFormat): Boolean = format != ExportFormat.CSV && gate.showAds.value

    val removeAdsPrice: StateFlow<String?> = billing.price

    fun export(format: ExportFormat) {
        val period = state.value.period ?: return
        if (_exporting.value != null) return
        _exporting.value = format
        viewModelScope.launch {
            try {
                val uri = exporter.export(period, format, jobId = state.value.selectedJobId)
                _events.send(ExportEvent.Share(exporter.shareIntent(uri, format)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ExportEvent.Failed(format))
            } finally {
                _exporting.value = null
            }
        }
    }

    /** The user chose to watch the ad. No fill / offline still exports: their data shouldn't need the network. */
    fun watchAdThenExport(activity: Activity, format: ExportFormat) {
        viewModelScope.launch {
            when (rewarded.show(activity)) {
                RewardResult.EARNED, RewardResult.UNAVAILABLE -> export(format)
                RewardResult.NOT_EARNED -> Unit
            }
        }
    }

    fun buyRemoveAds(activity: Activity): Boolean = billing.purchase(activity)

    private val offset = MutableStateFlow(0)
    private val jobFilter = MutableStateFlow<Long?>(null)

    /** Null = all jobs. Also decides what gets exported. */
    fun selectJob(jobId: Long?) { jobFilter.value = jobId }
    private val ticker = flow { while (true) { emit(clock.instant()); delay(30_000) } }

    private fun today() = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()

    private val key = combine(settingsRepo.settings, offset) { s, off ->
        var p = PayPeriods.containing(today(), s.payPeriod)
        repeat(kotlin.math.abs(off)) { p = if (off < 0) PayPeriods.previous(p, s.payPeriod) else PayPeriods.next(p, s.payPeriod) }
        PeriodKey(p, s.payPeriod, s.weekStart, off)
    }.distinctUntilChanged()

    private val reloads = MutableStateFlow(0)
    fun reload() { reloads.value++ }

    val state: StateFlow<ReportsUiState> = retryable(reloads, { ReportsUiState(loading = false, loadError = true) }) {
      key.flatMapLatest { k ->
        combine(
            repo.observeShifts(HoursCalculator.loadFrom(k.period.start, k.weekStart), k.period.endInclusive),
            repo.observeMarks(k.period.start, k.period.endInclusive),
            repo.activeJobs,
            ticker,
            jobFilter,
        ) { shifts, marks, jobs, now, filter ->
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
            val all = jobs.mapIndexed { i, j ->
                val label = j.toLabel()
                JobReport(label, ts.perJob[i], label.scheduledPerWeek?.let { Duration.ofSeconds(it.seconds * days / 7) })
            }
            val selected = filter?.takeIf { id -> all.any { it.job.id == id } } // a removed job falls back to All
            val reports = all
                .filter { selected == null || it.job.id == selected }
                .sortedWith(compareByDescending<JobReport> { it.hours }.thenBy { it.job.name.lowercase() })
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
                totalHours = reports.fold(Duration.ZERO) { a, r -> a.plus(r.hours) },
                totalOvertime = reports.fold(Duration.ZERO) { a, r -> a.plus(r.summary.overtime).plus(r.summary.doubleTime) },
                jobOptions = all.map { it.job },
                selectedJobId = selected,
            )
        }
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
