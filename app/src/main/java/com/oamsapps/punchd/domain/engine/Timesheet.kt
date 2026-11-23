package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.DayFlag
import com.oamsapps.punchd.domain.model.DayMarkEntry
import com.oamsapps.punchd.domain.model.JobPeriodSummary
import com.oamsapps.punchd.domain.model.RuleSet
import com.oamsapps.punchd.domain.model.WorkShift
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the engine needs to know about a job. */
data class JobInfo(
    val id: Long,
    val rules: RuleSet,
    val hourlyRate: BigDecimal,
    /** Planned hours per week, for "worked vs scheduled". Null = not set. */
    val scheduledPerWeek: Duration? = null,
)

data class ShiftRow(
    val shift: WorkShift,
    val worked: Duration,
    val breaks: Duration,
)

data class TimesheetDay(
    val date: LocalDate,
    val shifts: List<ShiftRow>,
    val worked: Duration,
    /** Overtime + double time across jobs. */
    val overtime: Duration,
    val paidLeave: Duration,
    val marks: List<DayMarkEntry>,
    val flags: Set<DayFlag>,
)

data class Timesheet(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<TimesheetDay>,
    val perJob: List<JobPeriodSummary>,
    val worked: Duration,
    val regular: Duration,
    val overtime: Duration,
    val doubleTime: Duration,
    val paidLeave: Duration,
    /** Scheduled hours for the range, pro-rated by days (null when no job has a schedule). */
    val scheduled: Duration?,
)

/**
 * Builds the Timesheet tab (all jobs together) and the Reports tab (one summary per job).
 * Each job keeps its own overtime rules; the day view adds the jobs up.
 *
 * [shifts] must start at `HoursCalculator.loadFrom(from, weekStart)` so weekly OT is right.
 */
object TimesheetBuilder {

    fun build(
        jobs: List<JobInfo>,
        shifts: List<WorkShift>,
        marks: List<DayMarkEntry>,
        weekStart: DayOfWeek,
        from: LocalDate,
        to: LocalDate,
        now: Instant,
    ): Timesheet {
        val perJob = jobs.map { job ->
            HoursCalculator.summarize(job.id, shifts, marks, job.rules, job.hourlyRate, weekStart, from, to, now)
        }
        val shiftsByDate = shifts.groupBy { it.workDate }
        val marksByDate = marks.filter { m -> jobs.any { it.id == m.jobId } }.groupBy { it.date }
        val jobIds = jobs.map { it.id }.toSet()

        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
            val jobDays = perJob.mapNotNull { s -> s.days.firstOrNull { it.date == date } }
            val rows = shiftsByDate[date].orEmpty()
                .filter { it.jobId in jobIds }
                .sortedBy { it.start }
                .map { s ->
                    val end = s.end ?: now
                    val gross = Duration.between(s.start, end).let { if (it.isNegative) Duration.ZERO else it }
                    val brk = HoursCalculator.breakTotal(s.breaks, s.start, end, now)
                    ShiftRow(s, gross.minus(brk).let { if (it.isNegative) Duration.ZERO else it }, brk)
                }
            TimesheetDay(
                date = date,
                shifts = rows,
                worked = jobDays.sum { it.worked },
                overtime = jobDays.sum { it.overtime.plus(it.doubleTime) },
                paidLeave = jobDays.sum { it.paidLeave },
                marks = marksByDate[date].orEmpty(),
                flags = jobDays.flatMapTo(mutableSetOf()) { it.flags },
            )
        }.toList()

        val dayCount = ChronoUnit.DAYS.between(from, to) + 1
        val weekly = jobs.mapNotNull { it.scheduledPerWeek }
        val scheduled = if (weekly.isEmpty()) null
        else Duration.ofSeconds(weekly.sumOf { it.seconds } * dayCount / 7)

        return Timesheet(
            from = from,
            to = to,
            days = days,
            perJob = perJob,
            worked = perJob.sum { it.worked },
            regular = perJob.sum { it.regular },
            overtime = perJob.sum { it.overtime },
            doubleTime = perJob.sum { it.doubleTime },
            paidLeave = perJob.sum { it.paidLeave },
            scheduled = scheduled,
        )
    }

    private inline fun <T> List<T>.sum(f: (T) -> Duration): Duration = fold(Duration.ZERO) { a, t -> a.plus(f(t)) }
}
