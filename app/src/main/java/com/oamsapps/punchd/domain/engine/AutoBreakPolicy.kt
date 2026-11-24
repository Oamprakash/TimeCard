package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.RuleSet
import java.time.Duration

/**
 * Decides how much break to auto-deduct when a shift is clocked out.
 *
 * The result is stored as a Break row with isAutoDeducted = true at clock-out, so changing the
 * rule later never rewrites past pay. Example: 30 min after 6 h -> a 7 h shift with a recorded
 * 10 min break gets a 20 min auto break.
 */
object AutoBreakPolicy {
    fun toDeduct(grossShift: Duration, recordedBreaks: Duration, rules: RuleSet): Duration {
        val after = rules.autoBreakAfter ?: return Duration.ZERO
        if (rules.autoBreak <= Duration.ZERO) return Duration.ZERO
        val worked = grossShift.minus(recordedBreaks)
        if (worked <= after) return Duration.ZERO
        val missing = rules.autoBreak.minus(recordedBreaks)
        return if (missing.isNegative) Duration.ZERO else missing
    }
}
