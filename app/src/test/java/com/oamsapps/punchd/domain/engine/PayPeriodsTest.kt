package com.oamsapps.punchd.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PayPeriodsTest {
    private fun p(a: String, b: String) = PayPeriod(d(a), d(b))
    private fun cfg(type: PayPeriodType, anchor: String) = PayPeriodConfig(type, d(anchor))

    @Test fun weekly() =
        assertEquals(p("2026-11-23", "2026-11-29"), PayPeriods.containing(d("2026-11-25"), cfg(PayPeriodType.WEEKLY, "2026-01-05")))

    @Test fun weekly_dateBeforeAnchor() =
        assertEquals(p("2025-12-29", "2026-01-04"), PayPeriods.containing(d("2026-01-04"), cfg(PayPeriodType.WEEKLY, "2026-01-05")))

    @Test fun biweekly() {
        val c = cfg(PayPeriodType.BIWEEKLY, "2026-01-05")
        assertEquals(p("2026-11-23", "2026-12-06"), PayPeriods.containing(d("2026-11-23"), c))
        assertEquals(p("2026-11-09", "2026-11-22"), PayPeriods.containing(d("2026-11-22"), c))
    }

    @Test fun semiMonthly() {
        val c = cfg(PayPeriodType.SEMI_MONTHLY, "2026-01-01")
        assertEquals(p("2026-11-01", "2026-11-15"), PayPeriods.containing(d("2026-11-15"), c))
        assertEquals(p("2028-02-16", "2028-02-29"), PayPeriods.containing(d("2028-02-20"), c)) // leap year
    }

    @Test fun monthly_anchor31_clampsInShortMonths() {
        val c = cfg(PayPeriodType.MONTHLY, "2026-01-31")
        assertEquals(p("2026-01-31", "2026-02-27"), PayPeriods.containing(d("2026-02-10"), c))
        assertEquals(p("2026-02-28", "2026-03-30"), PayPeriods.containing(d("2026-02-28"), c))
    }

    @Test fun monthly_anchor25() =
        assertEquals(p("2026-10-25", "2026-11-24"), PayPeriods.containing(d("2026-11-10"), cfg(PayPeriodType.MONTHLY, "2026-01-25")))

    @Test fun nextAndPrevious() {
        val c = cfg(PayPeriodType.SEMI_MONTHLY, "2026-01-01")
        val first = p("2026-11-01", "2026-11-15")
        assertEquals(p("2026-11-16", "2026-11-30"), PayPeriods.next(first, c))
        assertEquals(p("2026-10-16", "2026-10-31"), PayPeriods.previous(first, c))
    }
}
