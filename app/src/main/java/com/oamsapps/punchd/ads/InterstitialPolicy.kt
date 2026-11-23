package com.oamsapps.punchd.ads

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** What has been shown so far; persisted in DataStore. */
data class InterstitialHistory(
    val day: LocalDate? = null,
    val shownThatDay: Int = 0,
    val lastShown: Instant? = null,
)

/**
 * Spec: an interstitial only after saving a manual edit, at most 1 per 5 minutes and 2 per day.
 * Never on clock in/out or from the notification; those paths never call this.
 */
object InterstitialPolicy {
    val MIN_GAP: Duration = Duration.ofMinutes(5)
    const val MAX_PER_DAY = 2

    fun canShow(h: InterstitialHistory, now: Instant, today: LocalDate): Boolean {
        val countToday = if (h.day == today) h.shownThatDay else 0
        if (countToday >= MAX_PER_DAY) return false
        val last = h.lastShown ?: return true
        // A clock set backwards (last in the future) counts as "too soon" rather than unlocking ads.
        return !now.isBefore(last.plus(MIN_GAP))
    }

    fun recordShown(h: InterstitialHistory, now: Instant, today: LocalDate): InterstitialHistory =
        InterstitialHistory(
            day = today,
            shownThatDay = (if (h.day == today) h.shownThatDay else 0) + 1,
            lastShown = now,
        )
}
