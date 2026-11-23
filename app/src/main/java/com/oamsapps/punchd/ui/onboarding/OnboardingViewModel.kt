package com.oamsapps.punchd.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.domain.engine.PayPeriodType
import com.oamsapps.punchd.domain.model.Countries
import com.oamsapps.punchd.domain.model.HoursInput
import com.oamsapps.punchd.domain.model.PresetKind
import com.oamsapps.punchd.domain.model.WorkCountry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { COUNTRY, JOB, PAY_PERIOD, DONE }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.COUNTRY,
    val country: WorkCountry = WorkCountry.OTHER,
    val preset: PresetKind = PresetKind.CUSTOM,
    val jobName: String = "",
    val rate: String = "",
    val currency: String = "USD",
    val scheduledHours: String = "",
    val autoBreak: Boolean = false,
    val payType: PayPeriodType = PayPeriodType.WEEKLY,
    val payAnchor: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val jobError: Boolean = false,
    val saving: Boolean = false,
    /** Finishing failed; tapping Start again retries. */
    val saveFailed: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repo: TimeCardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val locale = Locale.getDefault()
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init { pickCountry(Countries.guess(locale.country)) }

    /** Applies the country's preset, currency, week start and usual pay period (all still editable). */
    fun pickCountry(c: WorkCountry) {
        val d = Countries.defaults(c)
        val weekStart = d.weekStart ?: WeekFields.of(locale).firstDayOfWeek
        val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        _state.update {
            it.copy(
                country = c,
                preset = d.preset,
                currency = d.currency ?: runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD"),
                weekStart = weekStart,
                payType = d.payPeriod,
                payAnchor = if (d.payPeriod == PayPeriodType.MONTHLY) today.withDayOfMonth(1) else HoursCalculator.weekStartOf(today, weekStart),
            )
        }
    }

    fun setJob(name: String = _state.value.jobName, rate: String = _state.value.rate, scheduled: String = _state.value.scheduledHours) =
        _state.update { it.copy(jobName = name, rate = rate, scheduledHours = scheduled, jobError = false) }

    fun setCurrency(code: String) = _state.update { it.copy(currency = code.uppercase().take(3)) }
    fun setAutoBreak(on: Boolean) = _state.update { it.copy(autoBreak = on) }
    fun setPayPeriod(config: PayPeriodConfig, weekStart: DayOfWeek) =
        _state.update { it.copy(payType = config.type, payAnchor = config.anchor, weekStart = weekStart) }

    fun next() {
        val s = _state.value
        when (s.step) {
            OnboardingStep.COUNTRY -> go(OnboardingStep.JOB)
            OnboardingStep.JOB -> if (jobValid(s)) go(OnboardingStep.PAY_PERIOD) else _state.update { it.copy(jobError = true) }
            OnboardingStep.PAY_PERIOD -> go(OnboardingStep.DONE)
            OnboardingStep.DONE -> Unit
        }
    }

    fun back() {
        val s = _state.value.step
        if (s.ordinal > 0) go(OnboardingStep.entries[s.ordinal - 1])
    }

    private fun go(step: OnboardingStep) = _state.update { it.copy(step = step) }

    private fun jobValid(s: OnboardingState): Boolean {
        val rate = s.rate.trim().replace(',', '.').toBigDecimalOrNull()
        val scheduledOk = runCatching { HoursInput.parseMinutes(s.scheduledHours) }.isSuccess
        val currencyOk = runCatching { Currency.getInstance(s.currency) }.isSuccess
        return s.jobName.isNotBlank() && rate != null && rate.signum() >= 0 && scheduledOk && currencyOk
    }

    /** Creates the first job and settings, then marks onboarding done (the root switches to the tabs). */
    fun finish() {
        val s = _state.value
        if (s.saving || !jobValid(s)) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
          try {
            val jobId = createdJobId ?: repo.addJob(
                name = s.jobName.trim(),
                color = 0xFF1E88E5.toInt(),
                hourlyRate = BigDecimal(s.rate.trim().replace(',', '.')),
                currency = s.currency,
                preset = s.preset,
                unlimitedJobs = true, // first job
                scheduledMinPerWeek = HoursInput.parseMinutes(s.scheduledHours),
            ).also { createdJobId = it }
            if (s.autoBreak) {
                repo.jobWithRules(jobId)?.let { j ->
                    repo.updateJob(j.job, j.rules.copy(autoBreakMin = 30, autoBreakAfterMin = 6 * 60))
                }
            }
            settings.setPayPeriod(PayPeriodConfig(s.payType, s.payAnchor))
            settings.setWeekStart(s.weekStart)
            settings.setSelectedJob(jobId)
            settings.setOnboardingDone()
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            _state.update { it.copy(saving = false, saveFailed = true) }
          }
        }
    }

    /** Set once the first job exists, so a retry after a later failure doesn't create it twice. */
    private var createdJobId: Long? = null
}
