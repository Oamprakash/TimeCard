package com.oam.timecard.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oam.timecard.data.repository.FREE_JOB_LIMIT
import com.oam.timecard.data.repository.JobLimitReachedException
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.domain.model.PresetKind
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeCardRepositoryTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }

    private lateinit var db: TimeCardDatabase
    private lateinit var clock: MutableClock
    private lateinit var repo: TimeCardRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TimeCardDatabase::class.java).build()
        clock = MutableClock(Instant.parse("2026-11-16T08:00:00Z"))
        repo = TimeCardRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    private suspend fun newJob(preset: PresetKind = PresetKind.US_FEDERAL) =
        repo.addJob("Store", 0xFF3366CC.toInt(), BigDecimal("20"), "USD", preset, unlimitedJobs = false)

    @Test fun clockInTwice_keepsOneRunningShift() = runTest {
        val job = newJob()
        val a = repo.clockIn(job, ZoneOffset.UTC)
        val b = repo.clockIn(job, ZoneOffset.UTC)
        assertEquals(a, b)
    }

    @Test fun clockOut_closesBreakAndEndsShift() = runTest {
        val job = newJob()
        repo.clockIn(job, ZoneOffset.UTC)
        clock.now = clock.now.plus(Duration.ofHours(2)); repo.startBreak()
        clock.now = clock.now.plus(Duration.ofMinutes(15)); repo.clockOut()
        assertNull(repo.runningShift.first())
        val shifts = db.shiftDao().getRangeForJob(job, java.time.LocalDate.MIN, java.time.LocalDate.MAX)
        assertEquals(1, shifts.single().breaks.size)
        assertTrue(shifts.single().breaks.all { it.endUtc != null })
    }

    @Test fun clockOut_appliesAutoDeduct() = runTest {
        val job = newJob()
        // Give this job 30 min auto-deduct after 6 h.
        val rules = db.jobDao().getWithRules(job)!!.rules
        db.ruleSetDao().update(rules.copy(autoBreakMin = 30, autoBreakAfterMin = 360))
        repo.clockIn(job, ZoneOffset.UTC)
        clock.now = clock.now.plus(Duration.ofHours(7)); repo.clockOut()
        val brk = db.shiftDao().getRangeForJob(job, java.time.LocalDate.MIN, java.time.LocalDate.MAX).single().breaks.single()
        assertTrue(brk.isAutoDeducted)
        assertEquals(Duration.ofMinutes(30), Duration.between(brk.startUtc, brk.endUtc))
    }

    @Test(expected = JobLimitReachedException::class)
    fun freeJobLimit() = runTest {
        repeat(FREE_JOB_LIMIT + 1) { newJob() }
    }
}
