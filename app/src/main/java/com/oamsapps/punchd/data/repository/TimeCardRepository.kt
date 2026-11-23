package com.oamsapps.punchd.data.repository

import androidx.room.withTransaction
import com.oamsapps.punchd.data.db.BreakEntity
import com.oamsapps.punchd.data.db.DayMarkEntity
import com.oamsapps.punchd.data.db.JobEntity
import com.oamsapps.punchd.data.db.JobWithRules
import com.oamsapps.punchd.data.db.RuleSetEntity
import com.oamsapps.punchd.data.db.ShiftEntity
import com.oamsapps.punchd.data.db.ShiftWithBreaks
import com.oamsapps.punchd.data.db.TimeCardDatabase
import com.oamsapps.punchd.data.db.toDomain
import com.oamsapps.punchd.data.db.toNewRuleSetEntity
import com.oamsapps.punchd.domain.engine.AutoBreakPolicy
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.PayPeriod
import com.oamsapps.punchd.domain.engine.ResolvedBreak
import com.oamsapps.punchd.domain.engine.ShiftDraft
import com.oamsapps.punchd.domain.engine.ShiftDrafts
import com.oamsapps.punchd.domain.engine.ShiftError
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.domain.model.JobPeriodSummary
import com.oamsapps.punchd.domain.model.PresetKind
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

sealed interface SaveShiftResult {
    data class Saved(val id: Long) : SaveShiftResult
    data class Invalid(val errors: Set<ShiftError>) : SaveShiftResult
    data object NotFound : SaveShiftResult
}

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
    /** Including archived jobs (Settings). */
    val allJobs: Flow<List<JobWithRules>> = jobs.observeAll()
    val runningShift: Flow<ShiftWithBreaks?> = shifts.observeRunning()

    suspend fun addJob(
        name: String,
        color: Int,
        hourlyRate: BigDecimal,
        currency: String,
        preset: PresetKind,
        unlimitedJobs: Boolean,
        scheduledMinPerWeek: Int? = null,
    ): Long = db.withTransaction {
        if (!unlimitedJobs && jobs.activeCount() >= FREE_JOB_LIMIT) throw JobLimitReachedException()
        val ruleSetId = rules.insert(preset.toNewRuleSetEntity())
        jobs.insert(
            JobEntity(
                name = name, color = color, hourlyRate = hourlyRate, currency = currency, ruleSetId = ruleSetId,
                scheduledMinPerWeek = scheduledMinPerWeek,
            )
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

    suspend fun running(): ShiftWithBreaks? = shifts.getRunning()

    /** Saves a job and its own rules row together. */
    suspend fun updateJob(job: JobEntity, rules: RuleSetEntity) = db.withTransaction {
        require(job.ruleSetId == rules.id) { "Rules belong to another job" }
        this.rules.update(rules)
        jobs.update(job)
    }

    /**
     * Archived jobs disappear from the Clock tab and new entries but keep all their history.
     * @return false if the job is clocked in right now (clock out first).
     */
    suspend fun setArchived(jobId: Long, archived: Boolean): Boolean = db.withTransaction {
        if (archived && shifts.getRunning()?.shift?.jobId == jobId) return@withTransaction false
        val job = jobs.getWithRules(jobId)?.job ?: return@withTransaction false
        jobs.update(job.copy(archived = archived))
        true
    }

    suspend fun jobWithRules(jobId: Long): JobWithRules? = jobs.getWithRules(jobId)

    /** All jobs' shifts whose local start date is in [from]..[to]. */
    fun observeShifts(from: LocalDate, to: LocalDate): Flow<List<ShiftWithBreaks>> = shifts.observeRange(from, to)

    suspend fun setRunningNote(note: String?) = db.withTransaction {
        val running = shifts.getRunning() ?: return@withTransaction
        shifts.update(running.shift.copy(note = note?.trim()?.ifEmpty { null }))
    }

    fun observeMarks(from: LocalDate, to: LocalDate): Flow<List<DayMarkEntity>> = marks.observeRange(from, to)

    suspend fun getShift(id: Long): ShiftWithBreaks? = shifts.getById(id)

    /**
     * Adds or edits a shift typed by the user. Validates (no future times, breaks inside the shift,
     * no overlap with other shifts) before writing anything. Editing a running shift only changes its
     * start, job and note; its end and breaks stay under the Clock tab's control.
     */
    suspend fun saveShift(draft: ShiftDraft, zone: ZoneId = ZoneId.systemDefault()): SaveShiftResult = db.withTransaction {
        val existing = draft.id?.let { shifts.getById(it) }
        if (draft.id != null && existing == null) return@withTransaction SaveShiftResult.NotFound
        val now = clock.instant()
        val running = existing != null && existing.shift.endUtc == null
        val shiftZone = existing?.shift?.zoneId ?: zone

        val resolved = ShiftDrafts.resolve(draft, shiftZone).let { r ->
            if (running) r.copy(
                end = null,
                breaks = existing!!.breaks.filter { it.endUtc != null }.map { ResolvedBreak(it.startUtc, it.endUtc!!) },
            ) else r
        }
        val errors = buildSet {
            if (!running && resolved.end == null) add(ShiftError.EMPTY) // a new shift needs an end; use Clock in instead
            val overlaps = shifts.countOverlapping(resolved.start, resolved.end ?: now, draft.id ?: 0)
            addAll(ShiftDrafts.validate(resolved, now, overlaps))
            val openBreak = existing?.breaks?.firstOrNull { it.endUtc == null }
            if (running && openBreak != null && openBreak.startUtc.isBefore(resolved.start)) add(ShiftError.BREAK_OUTSIDE_SHIFT)
        }
        if (errors.isNotEmpty()) return@withTransaction SaveShiftResult.Invalid(errors)

        val entity = ShiftEntity(
            id = draft.id ?: 0,
            jobId = draft.jobId,
            startUtc = resolved.start,
            endUtc = resolved.end,
            zoneId = shiftZone,
            workDate = resolved.start.atZone(shiftZone).toLocalDate(),
            note = draft.note?.trim()?.ifEmpty { null },
            edited = true,
        )
        val id = if (existing == null) shifts.insert(entity) else { shifts.update(entity); entity.id }
        if (!running) {
            shifts.deleteBreaksOf(id)
            resolved.breaks.forEach { shifts.insertBreak(BreakEntity(shiftId = id, startUtc = it.start, endUtc = it.end)) }
        }
        SaveShiftResult.Saved(id)
    }

    suspend fun deleteShift(id: Long) = shifts.deleteById(id)

    /** Sets or clears (type == null) the sick / holiday / paid-leave mark of one job on one day. */
    suspend fun setDayMark(date: LocalDate, jobId: Long, type: DayMarkType?, paidMinutes: Int) {
        if (type == null) marks.deleteFor(date, jobId)
        else marks.upsert(DayMarkEntity(date, jobId, type, paidMinutes.coerceIn(0, 24 * 60)))
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
