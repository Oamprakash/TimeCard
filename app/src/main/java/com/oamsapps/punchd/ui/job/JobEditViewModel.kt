package com.oamsapps.punchd.ui.job

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oamsapps.punchd.clock.ClockController
import com.oamsapps.punchd.data.db.JobEntity
import com.oamsapps.punchd.data.db.RuleSetEntity
import com.oamsapps.punchd.data.db.toNewRuleSetEntity
import com.oamsapps.punchd.data.repository.JobLimitReachedException
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.model.HoursInput
import com.oamsapps.punchd.domain.model.PresetKind
import com.oamsapps.punchd.util.sentenceCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
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

val JOB_COLORS = listOf(0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFFE53935, 0xFF00897B, 0xFF6D4C41, 0xFF546E7A).map { it.toInt() }

enum class JobFormError { NAME, RATE, CURRENCY, HOURS, MULTIPLIER, DOUBLE_BEFORE_OT, JOB_LIMIT, ARCHIVE_RUNNING, SAVE_FAILED }

/** Editor fields as typed. Hours are text so "7,5" works; blank = rule off. */
data class JobEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val rate: String = "",
    val currency: String = "USD",
    val color: Int = JOB_COLORS.first(),
    val scheduledHours: String = "",
    val preset: PresetKind = PresetKind.CUSTOM,
    val dailyOtHours: String = "",
    val dailyDoubleHours: String = "",
    val weeklyOtHours: String = "",
    val otMultiplier: String = "1.5",
    val doubleMultiplier: String = "2",
    val autoBreakMinutes: String = "",
    val autoBreakAfterHours: String = "",
    val maxDailyHours: String = "",
    val archived: Boolean = false,
    val error: JobFormError? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class JobEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TimeCardRepository,
    private val settings: SettingsRepository,
    private val controller: ClockController,
) : ViewModel() {

    companion object { const val ARG_ID = "id" }

    private val jobId: Long? = savedState.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private var original: Pair<JobEntity, RuleSetEntity>? = null

    private val _state = MutableStateFlow(JobEditState())
    val state: StateFlow<JobEditState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.CONFLATED)
    val done = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = jobId?.let { repo.jobWithRules(it) }
            if (existing != null) {
                original = existing.job to existing.rules
                val j = existing.job
                _state.value = fromRules(existing.rules, JobEditState(
                    loading = false, isNew = false, name = j.name, rate = j.hourlyRate.stripTrailingZeros().toPlainString(),
                    currency = j.currency, color = j.color, scheduledHours = HoursInput.format(j.scheduledMinPerWeek),
                    archived = j.archived,
                ))
            } else {
                val count = repo.allJobs.first().size
                val currency = runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
                val preset = repo.allJobs.first().firstOrNull()?.rules?.presetKind ?: PresetKind.CUSTOM
                _state.value = fromRules(preset.toNewRuleSetEntity(), JobEditState(
                    loading = false, currency = currency, color = JOB_COLORS[count % JOB_COLORS.size],
                ))
            }
        }
    }

    private fun fromRules(r: RuleSetEntity, base: JobEditState) = base.copy(
        preset = r.presetKind,
        dailyOtHours = HoursInput.format(r.dailyOtAfterMin),
        dailyDoubleHours = HoursInput.format(r.dailyDoubleAfterMin),
        weeklyOtHours = HoursInput.format(r.weeklyOtAfterMin),
        otMultiplier = r.otMultiplier.stripTrailingZeros().toPlainString(),
        doubleMultiplier = r.doubleMultiplier.stripTrailingZeros().toPlainString(),
        autoBreakMinutes = if (r.autoBreakMin > 0) r.autoBreakMin.toString() else "",
        autoBreakAfterHours = HoursInput.format(r.autoBreakAfterMin),
        maxDailyHours = HoursInput.format(r.maxDailyMin),
    )

    fun edit(f: JobEditState.() -> JobEditState) = _state.update { it.f().copy(error = null) }

    /** Fills the overtime fields from a preset; auto-break stays as the user set it. */
    fun applyPreset(kind: PresetKind) = _state.update { s ->
        fromRules(kind.toNewRuleSetEntity(), s).copy(
            autoBreakMinutes = s.autoBreakMinutes, autoBreakAfterHours = s.autoBreakAfterHours, error = null,
        )
    }

    fun setArchived(archived: Boolean) {
        val id = jobId ?: return
        viewModelScope.launch {
            val ok = try { repo.setArchived(id, archived) } catch (e: CancellationException) { throw e } catch (_: Exception) {
                _state.update { it.copy(error = JobFormError.SAVE_FAILED) }
                return@launch
            }
            if (ok) _state.update { it.copy(archived = archived, error = null) }
            else _state.update { it.copy(error = JobFormError.ARCHIVE_RUNNING) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val parsed = parse(s) ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val (job, rules) = parsed
                val orig = original
                if (orig == null) {
                    val id = repo.addJob(
                        job.name, job.color, job.hourlyRate, job.currency, rules.presetKind,
                        unlimitedJobs = settings.settings.first().removeAds,
                        scheduledMinPerWeek = job.scheduledMinPerWeek,
                    )
                    val created = repo.jobWithRules(id)!!
                    repo.updateJob(created.job, rules.copy(id = created.rules.id))
                } else {
                    repo.updateJob(
                        orig.first.copy(
                            name = job.name, color = job.color, hourlyRate = job.hourlyRate,
                            currency = job.currency, scheduledMinPerWeek = job.scheduledMinPerWeek,
                        ),
                        rules.copy(id = orig.second.id),
                    )
                }
                runCatching { controller.sync() } // job name in the notification, reminder rules
                _done.trySend(Unit)
            } catch (_: JobLimitReachedException) {
                _state.update { it.copy(saving = false, error = JobFormError.JOB_LIMIT) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, error = JobFormError.SAVE_FAILED) }
            }
        }
    }

    private fun parse(s: JobEditState): Pair<JobEntity, RuleSetEntity>? {
        fun fail(e: JobFormError): Nothing? { _state.update { it.copy(error = e) }; return null }
        if (s.name.isBlank()) return fail(JobFormError.NAME)
        val rate = s.rate.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 } ?: return fail(JobFormError.RATE)
        val currency = runCatching { Currency.getInstance(s.currency.trim().uppercase()).currencyCode }.getOrNull()
            ?: return fail(JobFormError.CURRENCY)
        val hours = runCatching {
            listOf(
                HoursInput.parseMinutes(s.scheduledHours),
                HoursInput.parseMinutes(s.dailyOtHours, 24),
                HoursInput.parseMinutes(s.dailyDoubleHours, 24),
                HoursInput.parseMinutes(s.weeklyOtHours),
                HoursInput.parseMinutes(s.autoBreakAfterHours, 24),
                HoursInput.parseMinutes(s.maxDailyHours, 24),
            )
        }.getOrNull() ?: return fail(JobFormError.HOURS)
        val autoBreak = s.autoBreakMinutes.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..240 } ?: return fail(JobFormError.HOURS)
        val ot = s.otMultiplier.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ONE && it <= BigDecimal(5) }
            ?: return fail(JobFormError.MULTIPLIER)
        val dbl = s.doubleMultiplier.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ONE && it <= BigDecimal(5) }
            ?: return fail(JobFormError.MULTIPLIER)
        val (scheduled, dailyOt, dailyDouble, weeklyOt, autoAfter, maxDaily) = hours
        if (dailyOt != null && dailyDouble != null && dailyDouble < dailyOt) return fail(JobFormError.DOUBLE_BEFORE_OT)

        val base = original?.second ?: s.preset.toNewRuleSetEntity()
        val presetRules = s.preset.toNewRuleSetEntity()
        val rules = base.copy(
            presetKind = s.preset,
            dailyOtAfterMin = dailyOt, dailyDoubleAfterMin = dailyDouble, weeklyOtAfterMin = weeklyOt,
            otMultiplier = ot, doubleMultiplier = dbl,
            autoBreakMin = autoBreak, autoBreakAfterMin = if (autoBreak > 0) autoAfter ?: 6 * 60 else autoAfter,
            maxDailyMin = maxDaily,
            // Break warnings follow the preset (Germany); other presets have none.
            breakWarn1AfterMin = presetRules.breakWarn1AfterMin, breakWarn1Min = presetRules.breakWarn1Min,
            breakWarn2AfterMin = presetRules.breakWarn2AfterMin, breakWarn2Min = presetRules.breakWarn2Min,
        )
        val job = JobEntity(
            name = s.name.sentenceCase(), color = s.color, hourlyRate = rate, currency = currency,
            ruleSetId = rules.id, scheduledMinPerWeek = scheduled,
        )
        return job to rules
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
