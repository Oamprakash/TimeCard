package com.oam.timecard.data.repository

import androidx.room.withTransaction
import com.oam.timecard.data.db.BreakEntity
import com.oam.timecard.data.db.JobEntity
import com.oam.timecard.data.db.JobWithRules
import com.oam.timecard.data.db.ShiftEntity
import com.oam.timecard.data.db.ShiftWithBreaks
import com.oam.timecard.data.db.TimeCardDatabase
import com.oam.timecard.data.db.toDomain
import com.oam.timecard.data.db.toNewRuleSetEntity
import com.oam.timecard.domain.engine.AutoBreakPolicy
import com.oam.timecard.domain.engine.HoursCalculator
import com.oam.timecard.domain.engine.PayPeriod
import com.oam.timecard.domain.model.JobPeriodSummary
import com.oam.timecard.domain.model.PresetKind
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

class JobLimitReachedException : IllegalStateException("Free version allows $FREE_JOB_LIMIT jobs")

const val FREE_JOB_LIMIT = 3

/**
 * Data-level operations for clocking, breaks and summaries. UI (Sprint 2+) talks only to this.
 * Every write that must not race (clock in/out, breaks) runs in one Room transaction.
 */
@Singleton
class TimeCardRepository @Inject constructor(
    private val db: TimeCardDatabase,
    private val clock: Clock,
) {
    private val jobs = db.jobDao()
    private val rules = db.ruleSetDao()
    private val shifts = db.shiftDao()
    private val marks = db.dayMarkDao()

    val activeJobs: Flow<List<JobWithRules>> = jobs.observeActive()
    val runningShift: Flow<ShiftWithBreaks?> = shifts.observeRunning()

    suspend fun addJob(
        name: String,
        color: Int,
        hourlyRate: BigDecimal,
        currency: String,
        preset: PresetKind,
        unlimitedJobs: Boolean,
    ): Long = db.withTransaction {
        if (!unlimitedJobs && jobs.activeCount() >= FREE_JOB_LIMIT) throw JobLimitReachedException()
        val ruleSetId = rules.insert(preset.toNewRuleSetEntity())
        jobs.insert(
            JobEntity(name = name, color = color, hourlyRate = hourlyRate, currency = currency, ruleSetId = ruleSetId)
        )
    }

    /** Idempotent: if a shift is already running, returns its id instead of starting a second one. */
    suspend fun clockIn(jobId: Long, zone: ZoneId = ZoneId.systemDefault(), note: String? = null): Long = db.withTransaction {
        shifts.getRunning()?.let { return@withTransaction it.shift.id }
        val now = clock.instant()
        shifts.insert(
            ShiftEntity(
                jobId = jobId, startUtc = now, endUtc = null, zoneId = zone,
                workDate = now.atZone(zone).toLocalDate(), note = note,
            )
        )
    }

    /** Ends any running break, applies the job's auto-deduct rule, then ends the shift. No-op if not clocked in. */
    suspend fun clockOut(): Long? = db.withTransaction {
        val running = shifts.getRunning() ?: return@withTransaction null
        val now = clock.instant()
        shifts.closeOpenBreaks(running.shift.id, now)

        val job = jobs.getWithRules(running.shift.jobId)
        if (job != null) {
            val gross = Duration.between(running.shift.startUtc, now)
            val recorded = HoursCalculator.breakTotal(
                running.toDomain().breaks, running.shift.startUtc, now, now,
            )
            val auto = AutoBreakPolicy.toDeduct(gross, recorded, job.rules.toDomain())
            if (!auto.isZero) {
                // Recorded as a break that ends at clock-out, so totals and exports show it.
                shifts.insertBreak(
                    BreakEntity(shiftId = running.shift.id, startUtc = now.minus(auto), endUtc = now, isAutoDeducted = true)
                )
            }
        }
        shifts.update(running.shift.copy(endUtc = now))
        running.shift.id
    }

    suspend fun startBreak(): Boolean = db.withTransaction {
        val running = shifts.getRunning() ?: return@withTransaction false
        if (shifts.getOpenBreak(running.shift.id) != null) return@withTransaction true
        shifts.insertBreak(BreakEntity(shiftId = running.shift.id, startUtc = clock.instant(), endUtc = null))
        true
    }

    suspend fun endBreak(): Boolean = db.withTransaction {
        val running = shifts.getRunning() ?: return@withTransaction false
        shifts.closeOpenBreaks(running.shift.id, clock.instant())
        true
    }

    /** Pay-period summary for one job. Loads from the start of the first workweek so weekly OT is right. */
    suspend fun summarize(jobId: Long, period: PayPeriod, weekStart: DayOfWeek): JobPeriodSummary? {
        val job = jobs.getWithRules(jobId) ?: return null
        val from = HoursCalculator.loadFrom(period.start, weekStart)
        return HoursCalculator.summarize(
            jobId = jobId,
            shifts = shifts.getRangeForJob(jobId, from, period.endInclusive).map { it.toDomain() },
            marks = marks.getRangeForJob(jobId, period.start, period.endInclusive).map { it.toDomain() },
            rules = job.rules.toDomain(),
            hourlyRate = job.job.hourlyRate,
            weekStart = weekStart,
            start = period.start,
            endInclusive = period.endInclusive,
            now = clock.instant(),
        )
    }
}
