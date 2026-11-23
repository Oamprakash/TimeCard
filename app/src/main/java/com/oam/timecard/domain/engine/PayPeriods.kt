package com.oam.timecard.domain.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class PayPeriodType { WEEKLY, BIWEEKLY, SEMI_MONTHLY, MONTHLY }

/**
 * [anchor] is the start date of any one real pay period, set in onboarding.
 * WEEKLY/BIWEEKLY repeat every 7/14 days from it; MONTHLY uses its day-of-month
 * (clamped in short months: anchor 31 -> Feb 28/29); SEMI_MONTHLY is always 1–15 and 16–end.
 */
data class PayPeriodConfig(val type: PayPeriodType, val anchor: LocalDate)

data class PayPeriod(val start: LocalDate, val endInclusive: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(endInclusive)
}

object PayPeriods {

    fun containing(date: LocalDate, config: PayPeriodConfig): PayPeriod = when (config.type) {
        PayPeriodType.WEEKLY -> fixedLength(date, config.anchor, 7)
        PayPeriodType.BIWEEKLY -> fixedLength(date, config.anchor, 14)
        PayPeriodType.SEMI_MONTHLY ->
            if (date.dayOfMonth <= 15) PayPeriod(date.withDayOfMonth(1), date.withDayOfMonth(15))
            else PayPeriod(date.withDayOfMonth(16), date.withDayOfMonth(date.lengthOfMonth()))
        PayPeriodType.MONTHLY -> monthly(date, config.anchor.dayOfMonth)
    }

    fun previous(period: PayPeriod, config: PayPeriodConfig) = containing(period.start.minusDays(1), config)
    fun next(period: PayPeriod, config: PayPeriodConfig) = containing(period.endInclusive.plusDays(1), config)

    private fun fixedLength(date: LocalDate, anchor: LocalDate, length: Long): PayPeriod {
        val offset = Math.floorDiv(ChronoUnit.DAYS.between(anchor, date), length)
        val start = anchor.plusDays(offset * length)
        return PayPeriod(start, start.plusDays(length - 1))
    }

    private fun monthStartDay(monthRef: LocalDate, anchorDay: Int): LocalDate =
        monthRef.withDayOfMonth(minOf(anchorDay, monthRef.lengthOfMonth()))

    private fun monthly(date: LocalDate, anchorDay: Int): PayPeriod {
        var start = monthStartDay(date, anchorDay)
        if (start.isAfter(date)) start = monthStartDay(date.minusMonths(1), anchorDay)
        val nextStart = monthStartDay(start.plusMonths(1).withDayOfMonth(1), anchorDay)
        return PayPeriod(start, nextStart.minusDays(1))
    }
}
