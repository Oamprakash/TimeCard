package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.WorkShift
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningStateTest {
    private val start = at("2026-11-23T09:00", NEW_YORK)

    @Test fun working_noBreaks() {
        val now = at("2026-11-23T11:15", NEW_YORK)
        val snap = RunningState.snapshot(WorkShift(1, 7, start, null, NEW_YORK), now)
        assertEquals(hours(2.25), snap.worked)
        assertFalse(snap.onBreak)
        assertNull(snap.breakStart)
        assertEquals(7L, snap.jobId)
        // Chronometer base = now - worked, i.e. the shift start when there were no breaks.
        assertEquals(start.toEpochMilli(), snap.chronometerBase(now))
    }

    @Test fun working_afterFinishedBreak_chronometerExcludesBreak() {
        val now = at("2026-11-23T12:00", NEW_YORK)
        val shift = WorkShift(1, 1, start, null, NEW_YORK, listOf(brk("2026-11-23T10:00", "2026-11-23T10:30", NEW_YORK)))
        val snap = RunningState.snapshot(shift, now)
        assertEquals(hours(2.5), snap.worked)
        assertEquals(mins(30), snap.breakTotal)
        assertEquals(now.minus(hours(2.5)).toEpochMilli(), snap.chronometerBase(now))
    }

    @Test fun onBreak_chronometerCountsTheBreak_workedIsFrozen() {
        val breakStart = at("2026-11-23T11:00", NEW_YORK)
        val shift = WorkShift(1, 1, start, null, NEW_YORK, listOf(brk("2026-11-23T11:00", null, NEW_YORK)))
        val at1130 = RunningState.snapshot(shift, at("2026-11-23T11:30", NEW_YORK))
        val at1145 = RunningState.snapshot(shift, at("2026-11-23T11:45", NEW_YORK))
        assertTrue(at1130.onBreak)
        assertEquals(breakStart, at1130.breakStart)
        assertEquals(hours(2.0), at1130.worked)
        assertEquals(hours(2.0), at1145.worked)
        assertEquals(mins(45), at1145.breakTotal)
        assertEquals(breakStart.toEpochMilli(), at1145.chronometerBase(at("2026-11-23T11:45", NEW_YORK)))
    }

    @Test fun clockSkewBeforeStart_neverNegative() {
        val snap = RunningState.snapshot(WorkShift(1, 1, start, null, NEW_YORK), start.minusSeconds(60))
        assertEquals(Duration.ZERO, snap.worked)
    }

    @Test fun durationParts() {
        assertEquals("0:00:00", DurationParts.clock(Duration.ZERO))
        assertEquals("3:07:09", DurationParts.clock(Duration.ofSeconds(3 * 3600 + 7 * 60 + 9)))
        assertEquals("27:00:00", DurationParts.clock(hours(27.0)))
        assertEquals("0:00:00", DurationParts.clock(Duration.ofSeconds(-5)))
    }

    @Test fun progress() {
        assertEquals(0.5f, DurationParts.progress(hours(20.0), hours(40.0)), 0.0001f)
        assertEquals(1f, DurationParts.progress(hours(45.0), hours(40.0)), 0.0001f)
        assertEquals(0f, DurationParts.progress(hours(10.0), null), 0.0001f)
    }
}
