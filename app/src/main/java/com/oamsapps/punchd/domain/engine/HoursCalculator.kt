package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.BreakSpan
import com.oamsapps.punchd.domain.model.DayFlag
import com.oamsapps.punchd.domain.model.DayMarkEntry
import com.oamsapps.punchd.domain.model.DaySummary
import com.oamsapps.punchd.domain.model.JobPeriodSummary
import com.oamsapps.punchd.domain.model.RuleSet
import com.oamsapps.punchd.domain.model.WorkShift
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Pure calculation engine: hours, overtime, double time and gross pay for ONE job.
 *
 * Rules (from the spec):
 *  - Times are UTC instants + zone; elapsed time is instant arithmetic, so DST nights are exact.
 *  - A shift counts toward the local date it started on.
 *  - Daily OT/DT is applied first; weekly OT only converts *regular* hours above the weekly
 *    threshold, walking the workweek in date order, so no hour is counted twice.
 *  - Day marks (sick/holiday/leave) are paid at the regular rate and never count toward OT.
 *
 * Each job is calculated independently (each job has its own rule set).
 */
object HoursCalculator {

    fun weekStartOf(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(weekStart))

    /**
     * Summarise [start]..[endInclusive] for one job.
     *
     * [shifts] must include every shift of the job from `weekStartOf(start)` onward, otherwise
     * weekly overtime in a period that starts mid-week is under-counted. Use [loadFrom].
     *
     * @param now used for a running shift or running break (end == null).
     */
    fun summarize(
        jobId: Long,
        shifts: List<WorkShift>,
        marks: List<DayMarkEntry>,
        rules: RuleSet,
        hourlyRate: BigDecimal,
        weekStart: DayOfWeek,
        start: LocalDate,
        endInclusive: LocalDate,
        now: Instant,
    ): JobPeriodSummary {
        require(!endInclusive.isBefore(start)) { "Period end before start" }

        val jobShifts = shifts.filter { it.jobId == jobId }
        val byDate = jobShifts.groupBy { it.workDate }
        val marksByDate = marks.filter { it.jobId == jobId }.associateBy { it.date }

        val calcFrom = weekStartOf(start, weekStart)
        val days = ArrayList<DaySummary>()
        var weekRegularSoFar = Duration.ZERO

        var date = calcFrom
        while (!date.isAfter(endInclusive)) {
            if (date.dayOfWeek == weekStart) weekRegularSoFar = Duration.ZERO

            val dayShifts = byDate[date].orEmpty()
            var worked = Duration.ZERO
            var breaks = Duration.ZERO
            var running = false
            for (s in dayShifts) {
                val end = s.end ?: now
                if (s.end == null) running = true
                val gross = nonNegative(Duration.between(s.start, end))
                val b = breakTotal(s.breaks, s.start, end, now)
                breaks += b
                worked += nonNegative(gross - b)
            }

            // 1) Daily split.
            var regular = worked
            var overtime = Duration.ZERO
            var doubleTime = Duration.ZERO
            rules.dailyDoubleAfter?.let { dt ->
                if (regular > dt) { doubleTime = regular - dt; regular = dt }
            }
            rules.dailyOtAfter?.let { ot ->
                if (regular > ot) { overtime = regular - ot; regular = ot }
            }

            // 2) Weekly: only regular hours above the threshold move to OT.
            var weeklyMoved = Duration.ZERO
            rules.weeklyOtAfter?.let { threshold ->
                val room = nonNegative(threshold - weekRegularSoFar)
                if (regular > room) {
                    weeklyMoved = regular - room
                    regular = room
                    overtime += weeklyMoved
                }
            }
            weekRegularSoFar += regular

            val mark = marksByDate[date]
            val flags = buildSet {
                if (running) add(DayFlag.RUNNING)
                if (overtime > weeklyMoved) add(DayFlag.DAILY_OVERTIME)
                if (doubleTime > Duration.ZERO) add(DayFlag.DOUBLE_TIME)
                if (weeklyMoved > Duration.ZERO) add(DayFlag.WEEKLY_OVERTIME)
                if (rules.breakRequirements.any { worked > it.workedOver && breaks < it.minBreak }) {
                    add(DayFlag.MISSING_BREAK)
                }
                rules.maxDaily?.let { if (worked > it) add(DayFlag.OVER_MAX_HOURS) }
            }

            if (!date.isBefore(start)) {
                days += DaySummary(
                    date = date,
                    worked = worked,
                    breaks = breaks,
                    regular = regular,
                    overtime = overtime,
                    doubleTime = doubleTime,
                    paidLeave = mark?.paidTime ?: Duration.ZERO,
                    mark = mark?.type,
                    flags = flags,
                )
            }
            date = date.plusDays(1)
        }

        val regular = days.sumDuration { it.regular }
        val overtime = days.sumDuration { it.overtime }
        val doubleTime = days.sumDuration { it.doubleTime }
        val leave = days.sumDuration { it.paidLeave }

        val pay = money(regular + leave, hourlyRate) +
            money(overtime, hourlyRate * rules.otMultiplier) +
            money(doubleTime, hourlyRate * rules.doubleMultiplier)

        return JobPeriodSummary(
            jobId = jobId,
            start = start,
            endInclusive = endInclusive,
            days = days,
            worked = days.sumDuration { it.worked },
            regular = regular,
            overtime = overtime,
            doubleTime = doubleTime,
            paidLeave = leave,
            grossPay = pay.setScale(2, RoundingMode.HALF_UP),
        )
    }

    /** First local date whose shifts must be loaded to summarise a period starting at [start]. */
    fun loadFrom(start: LocalDate, weekStart: DayOfWeek): LocalDate = weekStartOf(start, weekStart)

    /** Total break time inside [shiftStart, shiftEnd]; running breaks end at [now]. */
    internal fun breakTotal(breaks: List<BreakSpan>, shiftStart: Instant, shiftEnd: Instant, now: Instant): Duration =
        breaks.fold(Duration.ZERO) { acc, b ->
            val s = maxOf(b.start, shiftStart)
            val e = minOf(b.end ?: now, shiftEnd)
            acc + nonNegative(Duration.between(s, e))
        }

    private fun money(time: Duration, rate: BigDecimal): BigDecimal =
        BigDecimal.valueOf(time.seconds).multiply(rate).divide(BigDecimal.valueOf(3600), 10, RoundingMode.HALF_UP)

    private fun nonNegative(d: Duration) = if (d.isNegative) Duration.ZERO else d
    private inline fun <T> List<T>.sumDuration(f: (T) -> Duration): Duration =
        fold(Duration.ZERO) { acc, t -> acc.plus(f(t)) }
}
