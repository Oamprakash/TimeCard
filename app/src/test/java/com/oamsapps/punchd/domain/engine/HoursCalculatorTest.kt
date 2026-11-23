package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.DayFlag
import com.oamsapps.punchd.domain.model.DayMarkEntry
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.domain.model.JobPeriodSummary
import com.oamsapps.punchd.domain.model.RulePresets
import com.oamsapps.punchd.domain.model.RuleSet
import com.oamsapps.punchd.domain.model.WorkShift
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoursCalculatorTest {

    private fun run(
        shifts: List<WorkShift>,
        rules: RuleSet,
        from: LocalDate,
        to: LocalDate,
        rate: String = "20",
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        marks: List<DayMarkEntry> = emptyList(),
        now: Instant = Instant.parse("2030-01-01T00:00:00Z"),
        jobId: Long = 1,
    ): JobPeriodSummary = HoursCalculator.summarize(
        jobId, shifts, marks, rules, BigDecimal(rate), weekStart, from, to, now,
    )

    private fun assertHours(expected: Double, actual: Duration, what: String = "") =
        assertEquals("$what hours", hours(expected), actual)

    private fun assertPay(expected: String, s: JobPeriodSummary) =
        assertEquals(BigDecimal(expected), s.grossPay)

    private val WEEK_FROM = d("2026-11-16") // Monday
    private val WEEK_TO = d("2026-11-22")   // Sunday

    private fun weekdays(vararg lengths: Double, zone: java.time.ZoneId = NEW_YORK, start: LocalDate = WEEK_FROM) =
        lengths.mapIndexed { i, h -> shift(start.plusDays(i.toLong()).toString(), "08:00", h, zone) }

    // ---------- US federal ----------

    @Test fun usFederal_fiveNineHourDays_fiveHoursWeeklyOvertime() {
        val s = run(weekdays(9.0, 9.0, 9.0, 9.0, 9.0), RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO)
        assertHours(45.0, s.worked)
        assertHours(40.0, s.regular)
        assertHours(5.0, s.overtime)
        assertHours(0.0, s.doubleTime)
        assertPay("950.00", s) // 40*20 + 5*30
        val friday = s.days.first { it.date == d("2026-11-20") }
        assertHours(4.0, friday.regular, "Fri regular")
        assertHours(5.0, friday.overtime, "Fri OT")
        assertTrue(DayFlag.WEEKLY_OVERTIME in friday.flags)
        assertFalse(DayFlag.DAILY_OVERTIME in friday.flags)
    }

    @Test fun usFederal_fourTenHourDays_noOvertime() {
        val s = run(weekdays(10.0, 10.0, 10.0, 10.0), RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO)
        assertHours(40.0, s.regular)
        assertHours(0.0, s.overtime)
        assertPay("800.00", s)
    }

    // ---------- California ----------

    @Test fun california_thirteenHourDay_splitsRegularOvertimeDouble() {
        val s = run(weekdays(13.0, zone = LOS_ANGELES), RulePresets.CALIFORNIA, WEEK_FROM, WEEK_TO)
        assertHours(8.0, s.regular)
        assertHours(4.0, s.overtime)
        assertHours(1.0, s.doubleTime)
        assertPay("320.00", s) // 160 + 4*30 + 1*40
        val mon = s.days.first()
        assertTrue(DayFlag.DAILY_OVERTIME in mon.flags)
        assertTrue(DayFlag.DOUBLE_TIME in mon.flags)
    }

    @Test fun california_sixEightHourDays_sixthDayIsWeeklyOvertime() {
        val s = run(weekdays(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, zone = LOS_ANGELES), RulePresets.CALIFORNIA, WEEK_FROM, WEEK_TO)
        assertHours(40.0, s.regular)
        assertHours(8.0, s.overtime)
        assertPay("1040.00", s)
        assertTrue(DayFlag.WEEKLY_OVERTIME in s.days.first { it.date == d("2026-11-21") }.flags)
    }

    @Test fun california_dailyOvertimeIsNotCountedTwiceInWeekly() {
        // Mon–Thu 10 h (8 reg + 2 OT each), Fri 8 h, Sat 4 h. Regular reaches 40 on Fri, so Sat is all OT.
        val s = run(weekdays(10.0, 10.0, 10.0, 10.0, 8.0, 4.0, zone = LOS_ANGELES), RulePresets.CALIFORNIA, WEEK_FROM, WEEK_TO)
        assertHours(52.0, s.worked)
        assertHours(40.0, s.regular)
        assertHours(12.0, s.overtime)
        assertEquals(s.worked, s.regular + s.overtime + s.doubleTime)
        assertPay("1160.00", s)
    }

    // ---------- Germany ----------

    @Test fun germany_elevenHoursNoBreak_warnsButPaysStraightTime() {
        val s = run(weekdays(11.0, zone = BERLIN), RulePresets.GERMANY, WEEK_FROM, WEEK_TO, rate = "15")
        val mon = s.days.first()
        assertHours(11.0, s.regular)
        assertHours(0.0, s.overtime)
        assertPay("165.00", s)
        assertTrue(DayFlag.MISSING_BREAK in mon.flags)
        assertTrue(DayFlag.OVER_MAX_HOURS in mon.flags)
    }

    @Test fun germany_overNineHoursNeeds45MinBreak() {
        val with30 = shift("2026-11-16", "08:00", 10.0, BERLIN,
            breaks = listOf(brk("2026-11-16T12:00", "2026-11-16T12:30", BERLIN)))
        val s30 = run(listOf(with30), RulePresets.GERMANY, WEEK_FROM, WEEK_TO).days.first()
        assertHours(9.5, s30.worked)
        assertTrue(DayFlag.MISSING_BREAK in s30.flags)

        val with45 = shift("2026-11-16", "08:00", 10.0, BERLIN,
            breaks = listOf(brk("2026-11-16T12:00", "2026-11-16T12:45", BERLIN)))
        val s45 = run(listOf(with45), RulePresets.GERMANY, WEEK_FROM, WEEK_TO).days.first()
        assertHours(9.25, s45.worked)
        assertTrue(s45.flags.isEmpty())
    }

    @Test fun germany_exactlySixHoursWithoutBreak_noWarning() {
        val s = run(weekdays(6.0, zone = BERLIN), RulePresets.GERMANY, WEEK_FROM, WEEK_TO)
        assertTrue(s.days.first().flags.isEmpty())
    }

    // ---------- Midnight ----------

    @Test fun overnightShift_countsTowardStartDay() {
        val s = run(listOf(shift("2026-11-16", "22:00", 8.0, BERLIN)), RulePresets.GERMANY, WEEK_FROM, WEEK_TO)
        assertHours(8.0, s.days.first { it.date == d("2026-11-16") }.worked)
        assertHours(0.0, s.days.first { it.date == d("2026-11-17") }.worked)
    }

    @Test fun overnightShiftAcrossWeekBoundary_countsTowardWeekItStarted() {
        val shifts = weekdays(10.0, 10.0, 10.0, 10.0) +
            shift("2026-11-22", "20:00", 8.0, NEW_YORK) // Sun 20:00 -> Mon 04:00
        val week1 = run(shifts, RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO)
        assertHours(40.0, week1.regular)
        assertHours(8.0, week1.overtime)
        val week2 = run(shifts, RulePresets.US_FEDERAL, d("2026-11-23"), d("2026-11-29"))
        assertHours(0.0, week2.worked)
    }

    // ---------- Daylight saving ----------

    @Test fun dstSpringForward_berlin_nightShiftIsSevenRealHours() {
        // 2026-03-29 02:00 -> 03:00 in Europe/Berlin. 22:00 to 06:00 local = 7 h.
        val s = run(listOf(shiftLocal("2026-03-28T22:00", "2026-03-29T06:00", BERLIN)),
            RulePresets.GERMANY, d("2026-03-23"), d("2026-03-29"))
        assertHours(7.0, s.days.first { it.date == d("2026-03-28") }.worked)
    }

    @Test fun dstFallBack_california_nightShiftIsNineRealHours_withDailyOvertime() {
        // 2026-11-01 02:00 -> 01:00 in America/Los_Angeles. 22:00 to 06:00 local = 9 h.
        val s = run(listOf(shiftLocal("2026-10-31T22:00", "2026-11-01T06:00", LOS_ANGELES)),
            RulePresets.CALIFORNIA, d("2026-10-26"), d("2026-11-01"))
        assertHours(9.0, s.worked)
        assertHours(8.0, s.regular)
        assertHours(1.0, s.overtime)
    }

    @Test fun dstWeek_usFederal_weeklyOvertimeUsesRealHours() {
        val shifts = weekdays(9.0, 9.0, 9.0, 9.0, start = d("2026-10-26")) +
            shiftLocal("2026-10-31T22:00", "2026-11-01T06:00", NEW_YORK) // 9 real hours
        val s = run(shifts, RulePresets.US_FEDERAL, d("2026-10-26"), d("2026-11-01"))
        assertHours(45.0, s.worked)
        assertHours(40.0, s.regular)
        assertHours(5.0, s.overtime)
        assertTrue(DayFlag.WEEKLY_OVERTIME in s.days.first { it.date == d("2026-10-31") }.flags)
    }

    // ---------- Breaks & running shifts ----------

    @Test fun recordedBreakIsDeducted() {
        val sh = shift("2026-11-16", "08:00", 9.0, NEW_YORK,
            breaks = listOf(brk("2026-11-16T12:00", "2026-11-16T13:00", NEW_YORK)))
        val day = run(listOf(sh), RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO).days.first()
        assertHours(8.0, day.worked)
        assertHours(1.0, day.breaks)
    }

    @Test fun breakOutsideShiftIsClipped() {
        val sh = shift("2026-11-16", "08:00", 9.0, NEW_YORK, // ends 17:00
            breaks = listOf(brk("2026-11-16T16:30", "2026-11-16T17:30", NEW_YORK)))
        assertHours(8.5, run(listOf(sh), RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO).days.first().worked)
    }

    @Test fun runningShiftWithRunningBreak_usesNow() {
        val start = at("2026-11-16T09:00", NEW_YORK)
        val running = WorkShift(999, 1, start, null, NEW_YORK, listOf(brk("2026-11-16T11:30", null, NEW_YORK)))
        val s = run(listOf(running), RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO, now = at("2026-11-16T12:00", NEW_YORK))
        val mon = s.days.first()
        assertHours(2.5, mon.worked)
        assertHours(0.5, mon.breaks)
        assertTrue(DayFlag.RUNNING in mon.flags)
    }

    // ---------- Day marks ----------

    @Test fun sickDayIsPaidButDoesNotCountTowardOvertime() {
        val marks = listOf(DayMarkEntry(d("2026-11-16"), 1, DayMarkType.SICK, hours(8.0)))
        val shifts = weekdays(10.0, 10.0, 10.0, 10.0, start = d("2026-11-17"))
        val s = run(shifts, RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO, marks = marks)
        assertHours(40.0, s.regular)
        assertHours(0.0, s.overtime)
        assertHours(8.0, s.paidLeave)
        assertEquals(DayMarkType.SICK, s.days.first().mark)
        assertPay("960.00", s)
    }

    // ---------- Pay periods that start mid-week ----------

    @Test fun periodStartingMidWeek_countsEarlierDaysOfThatWeekForOvertime() {
        // Week starts Sunday. Sun 15th (before the period) 10 h, then Mon–Thu 8 h.
        val shifts = listOf(shift("2026-11-15", "08:00", 10.0, NEW_YORK)) +
            weekdays(8.0, 8.0, 8.0, 8.0)
        val s = run(shifts, RulePresets.US_FEDERAL, d("2026-11-16"), d("2026-11-30"), weekStart = DayOfWeek.SUNDAY)
        assertEquals(d("2026-11-16"), s.days.first().date)
        assertHours(32.0, s.worked)
        assertHours(30.0, s.regular)
        assertHours(2.0, s.overtime)
        assertPay("660.00", s)
    }

    // ---------- Misc ----------

    @Test fun payIsRoundedHalfUpToCents() {
        val s = run(listOf(shift("2026-11-16", "08:00", 7.0 + 20.0 / 60, NEW_YORK)),
            RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO, rate = "17.33")
        assertPay("127.09", s) // 7h20m * 17.33 = 127.0866…
    }

    @Test fun otherJobsShiftsAreIgnored() {
        val shifts = weekdays(9.0, 9.0, 9.0, 9.0, 9.0) + shift("2026-11-21", "08:00", 9.0, NEW_YORK, jobId = 2)
        val s = run(shifts, RulePresets.US_FEDERAL, WEEK_FROM, WEEK_TO)
        assertHours(45.0, s.worked)
    }

    @Test fun customRules_noThresholds_allRegular() {
        val s = run(weekdays(14.0, 14.0, 14.0, 14.0), RulePresets.CUSTOM, WEEK_FROM, WEEK_TO)
        assertHours(56.0, s.regular)
        assertHours(0.0, s.overtime)
    }
}
