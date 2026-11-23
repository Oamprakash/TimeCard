package com.oam.timecard.domain.model

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A break inside a shift. [end] == null means the break is running. */
data class BreakSpan(val start: Instant, val end: Instant?)

/** A shift as the engine sees it. [end] == null means the user is still clocked in. */
data class WorkShift(
    val id: Long,
    val jobId: Long,
    val start: Instant,
    val end: Instant?,
    val zone: ZoneId,
    val breaks: List<BreakSpan> = emptyList(),
) {
    /** A shift counts toward the local date it started on (spec rule). */
    val workDate: LocalDate get() = start.atZone(zone).toLocalDate()
}

enum class DayMarkType { SICK, HOLIDAY, PAID_LEAVE }

/** Sick / holiday / paid leave. Paid at the regular rate, never counted toward overtime. */
data class DayMarkEntry(val date: LocalDate, val jobId: Long, val type: DayMarkType, val paidTime: Duration)

enum class DayFlag { RUNNING, DAILY_OVERTIME, DOUBLE_TIME, WEEKLY_OVERTIME, MISSING_BREAK, OVER_MAX_HOURS }

data class DaySummary(
    val date: LocalDate,
    val worked: Duration,
    val breaks: Duration,
    val regular: Duration,
    val overtime: Duration,
    val doubleTime: Duration,
    val paidLeave: Duration,
    val mark: DayMarkType?,
    val flags: Set<DayFlag>,
)

data class JobPeriodSummary(
    val jobId: Long,
    val start: LocalDate,
    val endInclusive: LocalDate,
    val days: List<DaySummary>,
    val worked: Duration,
    val regular: Duration,
    val overtime: Duration,
    val doubleTime: Duration,
    val paidLeave: Duration,
    val grossPay: BigDecimal,
)
