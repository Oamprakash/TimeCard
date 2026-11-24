package com.oam.timecard.domain.engine

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftDraftTest {
    private val now = at("2026-11-23T20:00", NEW_YORK)
    private fun t(s: String) = LocalTime.parse(s)

    private fun draft(start: String, end: String?, vararg breaks: Pair<String, String>, date: String = "2026-11-20") =
        ShiftDraft(jobId = 1, date = d(date), start = t(start), end = end?.let(::t),
            breaks = breaks.map { BreakDraft(t(it.first), t(it.second)) })

    private fun errors(dr: ShiftDraft, overlaps: Int = 0, zone: java.time.ZoneId = NEW_YORK) =
        ShiftDrafts.validate(ShiftDrafts.resolve(dr, zone), now, overlaps)

    @Test fun dayShift() {
        val r = ShiftDrafts.resolve(draft("08:00", "16:30", "12:00" to "12:30"), NEW_YORK)
        assertEquals(at("2026-11-20T08:00", NEW_YORK), r.start)
        assertEquals(at("2026-11-20T16:30", NEW_YORK), r.end)
        assertEquals(at("2026-11-20T12:00", NEW_YORK), r.breaks.single().start)
        assertTrue(errors(draft("08:00", "16:30", "12:00" to "12:30")).isEmpty())
    }

    @Test fun nightShift_endAndBreaksAfterMidnightRollToNextDay() {
        val r = ShiftDrafts.resolve(draft("22:00", "06:00", "23:50" to "00:20", "03:00" to "03:15"), NEW_YORK)
        assertEquals(at("2026-11-21T06:00", NEW_YORK), r.end)
        assertEquals(at("2026-11-20T23:50", NEW_YORK), r.breaks[0].start)
        assertEquals(at("2026-11-21T00:20", NEW_YORK), r.breaks[0].end)
        assertEquals(at("2026-11-21T03:00", NEW_YORK), r.breaks[1].start)
        assertTrue(errors(draft("22:00", "06:00", "23:50" to "00:20", "03:00" to "03:15")).isEmpty())
    }

    @Test fun dstSpringForward_nightShiftResolvesToRealInstants() {
        // Berlin 2026-03-29 02:00 -> 03:00. 22:00–06:00 is 7 real hours.
        val r = ShiftDrafts.resolve(draft("22:00", "06:00", date = "2026-03-28"), BERLIN)
        assertEquals(hours(7.0), java.time.Duration.between(r.start, r.end))
    }

    @Test fun runningShift_hasNoEnd() {
        val r = ShiftDrafts.resolve(draft("09:00", null, date = "2026-11-23"), NEW_YORK)
        assertNull(r.end)
        assertTrue(ShiftDrafts.validate(r, now, 0).isEmpty())
    }

    @Test fun sameStartAndEnd_isEmpty() =
        assertEquals(setOf(ShiftError.EMPTY), errors(draft("09:00", "09:00")))

    @Test fun futureShift_rejected() {
        val e = errors(draft("08:00", "16:00", date = "2026-11-24"))
        assertTrue(ShiftError.START_IN_FUTURE in e)
        assertTrue(ShiftError.END_IN_FUTURE in e)
    }

    @Test fun endLaterToday_rejected() =
        assertEquals(setOf(ShiftError.END_IN_FUTURE), errors(draft("18:00", "22:00", date = "2026-11-23")))

    @Test fun breakOutsideShift() =
        assertTrue(ShiftError.BREAK_OUTSIDE_SHIFT in errors(draft("08:00", "12:00", "12:30" to "13:00")))

    @Test fun backwardsBreak_isEmptyBreak() =
        assertTrue(ShiftError.BREAK_EMPTY in errors(draft("08:00", "16:00", "12:30" to "12:00")))

    @Test fun overlappingBreaks() =
        assertTrue(ShiftError.BREAKS_OVERLAP in errors(draft("08:00", "16:00", "12:00" to "12:30", "12:15" to "12:45")))

    @Test fun overlapsAnotherShift() =
        assertEquals(setOf(ShiftError.OVERLAPS_OTHER_SHIFT), errors(draft("08:00", "16:00"), overlaps = 1))
}
