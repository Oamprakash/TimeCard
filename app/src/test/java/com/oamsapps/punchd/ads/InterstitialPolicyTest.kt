package com.oamsapps.punchd.ads

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {
    private val today = LocalDate.parse("2026-11-23")
    private val t0 = Instant.parse("2026-11-23T09:00:00Z")

    @Test fun firstEverIsAllowed() = assertTrue(InterstitialPolicy.canShow(InterstitialHistory(), t0, today))

    @Test fun notWithinFiveMinutes() {
        val h = InterstitialPolicy.recordShown(InterstitialHistory(), t0, today)
        assertFalse(InterstitialPolicy.canShow(h, t0.plusSeconds(299), today))
        assertTrue(InterstitialPolicy.canShow(h, t0.plusSeconds(300), today))
    }

    @Test fun atMostTwoPerDay_resetsNextDay() {
        var h = InterstitialPolicy.recordShown(InterstitialHistory(), t0, today)
        h = InterstitialPolicy.recordShown(h, t0.plusSeconds(600), today)
        assertEquals(2, h.shownThatDay)
        assertFalse(InterstitialPolicy.canShow(h, t0.plusSeconds(3600), today))
        val tomorrow = today.plusDays(1)
        assertTrue(InterstitialPolicy.canShow(h, t0.plusSeconds(86_400), tomorrow))
        assertEquals(1, InterstitialPolicy.recordShown(h, t0.plusSeconds(86_400), tomorrow).shownThatDay)
    }

    @Test fun clockSetBackwards_isTooSoon() {
        val h = InterstitialPolicy.recordShown(InterstitialHistory(), t0, today)
        assertFalse(InterstitialPolicy.canShow(h, t0.minusSeconds(3600), today))
    }
}
