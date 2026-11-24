package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.RuleSet
import java.time.Duration
import java.time.Instant

enum class AlertType {
    /** User setting: "still clocked in?" after N hours since clock-in. */
    STILL_CLOCKED_IN,
    /** First break rule of the job (Germany: more than 6 h worked needs 30 min of breaks). */
    BREAK_SHORT,
    /** Second break rule (Germany: more than 9 h needs 45 min). */
    BREAK_LONG,
    /** Daily maximum (Germany: 10 h). */
    MAX_DAILY,
}

data class ReminderSettings(
    /** Null = off. */
    val stillClockedInAfter: Duration? = Duration.ofHours(10),
    /** Break and max-hours warnings from the job's rules. */
    val ruleWarnings: Boolean = true,
)

data class ReminderPlan(
    /** Alerts to post now (each at most once per shift). */
    val due: List<AlertType>,
    /** When to look again; null = nothing left to watch (or on a break, where worked time is frozen). */
    val nextCheck: Instant?,
)

/**
 * Decides which reminders are due for the running shift and when to check next, so the worker can
 * sleep until exactly then instead of polling. Worked time doesn't move during a break, so while on a
 * break only the "still clocked in" timer is scheduled; ending the break re-plans.
 */
object ReminderPlanner {

    fun plan(
        running: RunningSnapshot,
        rules: RuleSet,
        /** Worked time of shifts that already ended today (the 10 h limit is per day). */
        workedEarlierToday: Duration,
        settings: ReminderSettings,
        alreadySent: Set<AlertType>,
        now: Instant,
    ): ReminderPlan {
        data class Candidate(val type: AlertType, val due: Boolean, val at: Instant?)

        val candidates = mutableListOf<Candidate>()
        val dayWorked = workedEarlierToday.plus(running.worked)

        // Thresholds are strict ("more than 6 h"), so look one second after reaching them.
        fun whenWorkedReaches(threshold: Duration): Instant? =
            if (running.onBreak) null else now.plus(threshold.minus(dayWorked)).plusSeconds(1)

        settings.stillClockedInAfter?.let { after ->
            val at = running.start.plus(after)
            candidates += Candidate(AlertType.STILL_CLOCKED_IN, !now.isBefore(at), at)
        }

        if (settings.ruleWarnings) {
            rules.breakRequirements.sortedBy { it.workedOver }.take(2).forEachIndexed { i, req ->
                val type = if (i == 0) AlertType.BREAK_SHORT else AlertType.BREAK_LONG
                if (running.breakTotal >= req.minBreak) return@forEachIndexed // already took enough break
                val due = dayWorked > req.workedOver
                candidates += Candidate(type, due, if (due) null else whenWorkedReaches(req.workedOver))
            }
            rules.maxDaily?.let { max ->
                val due = dayWorked > max
                candidates += Candidate(AlertType.MAX_DAILY, due, if (due) null else whenWorkedReaches(max))
            }
        }

        val open = candidates.filter { it.type !in alreadySent }
        return ReminderPlan(
            due = open.filter { it.due }.map { it.type },
            nextCheck = open.filter { !it.due }.mapNotNull { it.at }.filter { it.isAfter(now) }.minOrNull(),
        )
    }
}
