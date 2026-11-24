package com.oamsapps.punchd.domain.model

import java.math.BigDecimal
import java.time.Duration

/**
 * The rules one job is paid and checked under. Thresholds are Durations so the engine never
 * works in fractional hours. A null threshold means "rule not used".
 */
data class RuleSet(
    val dailyOtAfter: Duration? = null,
    val dailyDoubleAfter: Duration? = null,
    val weeklyOtAfter: Duration? = null,
    val otMultiplier: BigDecimal = BigDecimal("1.5"),
    val doubleMultiplier: BigDecimal = BigDecimal("2.0"),
    /** Auto-deduct: once worked time passes [autoBreakAfter], top recorded breaks up to [autoBreak]. */
    val autoBreak: Duration = Duration.ZERO,
    val autoBreakAfter: Duration? = null,
    /** Warning only (e.g. German 10 h/day limit). Never changes pay. */
    val maxDaily: Duration? = null,
    /** Warning only: each rule says "worked more than X needs at least Y of breaks that day". */
    val breakRequirements: List<BreakRequirement> = emptyList(),
) {
    init {
        if (dailyOtAfter != null && dailyDoubleAfter != null) {
            require(dailyDoubleAfter >= dailyOtAfter) { "Double-time threshold must be >= overtime threshold" }
        }
    }
}

data class BreakRequirement(val workedOver: Duration, val minBreak: Duration)

enum class PresetKind { US_FEDERAL, CALIFORNIA, GERMANY, CUSTOM }

/** Built-in presets. Jobs copy one of these into their own editable rule row. */
object RulePresets {
    private fun h(hours: Long) = Duration.ofHours(hours)
    private fun m(minutes: Long) = Duration.ofMinutes(minutes)

    val US_FEDERAL = RuleSet(weeklyOtAfter = h(40))

    val CALIFORNIA = RuleSet(
        dailyOtAfter = h(8),
        dailyDoubleAfter = h(12),
        weeklyOtAfter = h(40),
    )

    /** ArbZG: no overtime pay rule by law; warnings for breaks (§4) and the 10 h/day cap (§3). */
    val GERMANY = RuleSet(
        maxDaily = h(10),
        breakRequirements = listOf(
            BreakRequirement(workedOver = h(6), minBreak = m(30)),
            BreakRequirement(workedOver = h(9), minBreak = m(45)),
        ),
    )

    val CUSTOM = RuleSet()

    fun forKind(kind: PresetKind): RuleSet = when (kind) {
        PresetKind.US_FEDERAL -> US_FEDERAL
        PresetKind.CALIFORNIA -> CALIFORNIA
        PresetKind.GERMANY -> GERMANY
        PresetKind.CUSTOM -> CUSTOM
    }
}
