package com.oam.timecard.domain.engine

import com.oam.timecard.domain.model.DayFlag
import com.oam.timecard.domain.model.DayMarkEntry
import com.oam.timecard.domain.model.DayMarkType
import com.oam.timecard.domain.model.RulePresets
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimesheetBuilderTest {
    private val now = Instant.parse("2030-01-01T00:00:00Z")
    private val store = JobInfo(1, RulePresets.US_FEDERAL, BigDecimal("20"), scheduledPerWeek = hours(40.0))
    private val bar = JobInfo(2, RulePresets.US_FEDERAL, BigDecimal("15"))

    private fun build(shifts: List<com.oam.timecard.domain.model.WorkShift>, marks: List<DayMarkEntry> = emptyList(),
                      from: String = "2026-11-16", to: String = "2026-11-22", jobs: List<JobInfo> = listOf(store, bar)) =
        TimesheetBuilder.build(jobs, shifts, marks, DayOfWeek.MONDAY, d(from), d(to), now)

    @Test fun twoJobs_sameDay_addUpButKeepSeparateOvertime() {
        // Job 1: 5 x 9 h = 45 h -> 5 h OT. Job 2: 2 x 5 h evenings, no OT on its own.
        val shifts = (0..4).map { shift("2026-11-${16 + it}", "08:00", 9.0, NEW_YORK, jobId = 1) } +
            listOf(shift("2026-11-16", "18:00", 5.0, NEW_YORK, jobId = 2), shift("2026-11-17", "18:00", 5.0, NEW_YORK, jobId = 2))
        val ts = build(shifts)
        assertEquals(7, ts.days.size)
        assertEquals(hours(55.0), ts.worked)
        assertEquals(hours(5.0), ts.overtime)
        val mon = ts.days.first()
        assertEquals(2, mon.shifts.size)
        assertEquals(hours(14.0), mon.worked)
        assertEquals(hours(5.0), ts.days.first { it.date == d("2026-11-20") }.overtime)
        assertTrue(DayFlag.WEEKLY_OVERTIME in ts.days.first { it.date == d("2026-11-20") }.flags)
        assertEquals(BigDecimal("950.00"), ts.perJob.first { it.jobId == 1L }.grossPay)
        assertEquals(BigDecimal("150.00"), ts.perJob.first { it.jobId == 2L }.grossPay)
    }

    @Test fun shiftRowsShowWorkedAndBreaks() {
        val sh = shift("2026-11-16", "08:00", 9.0, NEW_YORK, breaks = listOf(brk("2026-11-16T12:00", "2026-11-16T12:45", NEW_YORK)))
        val row = build(listOf(sh)).days.first().shifts.single()
        assertEquals(hours(8.25), row.worked)
        assertEquals(mins(45), row.breaks)
    }

    @Test fun dayMarksShowAndCountAsLeave() {
        val marks = listOf(DayMarkEntry(d("2026-11-18"), 1, DayMarkType.HOLIDAY, hours(8.0)))
        val ts = build(emptyList(), marks)
        val wed = ts.days.first { it.date == d("2026-11-18") }
        assertEquals(DayMarkType.HOLIDAY, wed.marks.single().type)
        assertEquals(hours(8.0), wed.paidLeave)
        assertEquals(hours(8.0), ts.paidLeave)
    }

    @Test fun scheduledHours_weekAndProRatedMonth() {
        assertEquals(hours(40.0), build(emptyList()).scheduled)
        // November 2026 has 30 days: 40 h x 30 / 7 = 171 h 25 m 42 s
        val month = build(emptyList(), from = "2026-11-01", to = "2026-11-30")
        assertEquals(java.time.Duration.ofSeconds(40L * 3600 * 30 / 7), month.scheduled)
        assertNull(build(emptyList(), jobs = listOf(bar)).scheduled)
    }

    @Test fun monthStartingMidWeek_usesEarlierDaysForWeeklyOvertime() {
        // Dec 2026 starts on Tuesday. Mon 30 Nov 10 h, then Tue–Fri 1–4 Dec 9 h each = 46 h in that week.
        val shifts = listOf(shift("2026-11-30", "08:00", 10.0, NEW_YORK)) +
            (1..4).map { shift("2026-12-0$it", "08:00", 9.0, NEW_YORK) }
        val ts = build(shifts, from = "2026-12-01", to = "2026-12-31", jobs = listOf(store))
        assertEquals(hours(36.0), ts.worked)
        assertEquals(hours(6.0), ts.overtime)
    }
}
