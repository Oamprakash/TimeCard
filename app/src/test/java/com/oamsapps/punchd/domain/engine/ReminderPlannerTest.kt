package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.RulePresets
import com.oamsapps.punchd.domain.model.WorkShift
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlannerTest {
    private val start = at("2026-11-23T08:00", BERLIN)

    private fun snap(now: Instant, vararg breaks: com.oamsapps.punchd.domain.model.BreakSpan) =
        RunningState.snapshot(WorkShift(1, 1, start, null, BERLIN, breaks.toList()), now)

    private fun plan(
        now: Instant,
        vararg breaks: com.oamsapps.punchd.domain.model.BreakSpan,
        earlier: Duration = Duration.ZERO,
        sent: Set<AlertType> = emptySet(),
        settings: ReminderSettings = ReminderSettings(),
        rules: com.oamsapps.punchd.domain.model.RuleSet = RulePresets.GERMANY,
    ) = ReminderPlanner.plan(snap(now, *breaks), rules, earlier, settings, sent, now)

    @Test fun justClockedIn_nextCheckJustAfterSixHours() {
        val p = plan(start.plusSeconds(60))
        assertTrue(p.due.isEmpty())
        assertEquals(start.plus(hours(6.0)).plusSeconds(1), p.nextCheck)
    }

    @Test fun overSixHoursWithoutBreak_breakWarningDue() {
        val p = plan(start.plus(hours(6.0)).plusSeconds(1))
        assertEquals(listOf(AlertType.BREAK_SHORT), p.due)
        // Next: the 9 h rule (still no break).
        assertEquals(start.plus(hours(9.0)).plusSeconds(1), p.nextCheck)
    }

    @Test fun alreadySent_isNotRepeated() {
        val p = plan(start.plus(hours(7.0)), sent = setOf(AlertType.BREAK_SHORT))
        assertTrue(p.due.isEmpty())
    }

    @Test fun thirtyMinuteBreakTaken_onlyNineHourAndMaxRulesRemain() {
        val b = brk("2026-11-23T12:00", "2026-11-23T12:30", BERLIN)
        val now = start.plus(hours(7.0))
        val p = plan(now, b)
        assertTrue(p.due.isEmpty())
        // 6.5 h worked; 9 h rule needs 45 min -> fires when worked passes 9 h, i.e. 2.5 h from now.
        assertEquals(now.plus(hours(2.5)).plusSeconds(1), p.nextCheck)
    }

    @Test fun tenHourDayIncludesEarlierShifts() {
        val now = start.plus(hours(3.0))
        // 7.5 h earlier today + 3 h now = 10.5 h > 10 h.
        val p = plan(now, earlier = hours(7.5), sent = setOf(AlertType.BREAK_SHORT, AlertType.BREAK_LONG))
        assertEquals(listOf(AlertType.MAX_DAILY), p.due)
    }

    @Test fun onBreak_onlyClockBasedReminderIsScheduled() {
        val now = at("2026-11-23T12:10", BERLIN)
        val p = plan(now, brk("2026-11-23T12:00", null, BERLIN))
        assertTrue(p.due.isEmpty())
        assertEquals(start.plus(hours(10.0)), p.nextCheck) // "still clocked in" after 10 h since clock-in
    }

    @Test fun stillClockedIn_usesElapsedTimeNotWorkedTime() {
        val now = start.plus(hours(10.0))
        val p = plan(now, settings = ReminderSettings(stillClockedInAfter = hours(10.0), ruleWarnings = false),
            rules = RulePresets.US_FEDERAL)
        assertEquals(listOf(AlertType.STILL_CLOCKED_IN), p.due)
        assertNull(p.nextCheck)
    }

    @Test fun everythingOff_nothingToDo() {
        val p = plan(start.plus(hours(12.0)), settings = ReminderSettings(stillClockedInAfter = null, ruleWarnings = false))
        assertTrue(p.due.isEmpty())
        assertNull(p.nextCheck)
    }

    @Test fun usPreset_hasNoRuleWarnings() {
        val p = plan(start.plus(hours(11.0)), rules = RulePresets.US_FEDERAL, settings = ReminderSettings(stillClockedInAfter = null))
        assertTrue(p.due.isEmpty())
    }
}
