package com.oamsapps.punchd.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.oamsapps.punchd.R
import com.oamsapps.punchd.util.formatMoneyNarrow
import com.oamsapps.punchd.domain.engine.DurationParts
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.Duration
import java.util.Currency
import java.util.Locale

/** "7h 05m" / "7 Std. 05 Min." */
@Composable
fun formatHm(d: Duration): String {
    val p = DurationParts.of(d)
    return stringResource(R.string.hours_minutes, p.hours, p.minutes)
}

/** "$ 12.00", "₹ 19.64", "12,00 €" — see [formatMoneyNarrow]. Use with tabular figures so decimals line up. */
fun formatMoney(amount: BigDecimal, currency: String): String = formatMoneyNarrow(amount, currency)

private val timeFormatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** Local wall-clock time of an instant in the zone it was recorded in, e.g. "9:05 AM" / "09:05". */
fun formatTime(instant: Instant, zone: ZoneId): String = timeFormatter.format(instant.atZone(zone))

fun formatTime(time: java.time.LocalTime): String = timeFormatter.format(time)

/** "Mon 23 Nov" style, following the device locale. */
fun formatDayShort(date: LocalDate): String =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(date)

fun formatDateMedium(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)

/** "Wed, 25 Nov 2026" / "Mi., 25. Nov. 2026", in the locale's own order. */
fun formatDateWithWeekday(date: LocalDate): String {
    val locale = java.util.Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyy")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

fun formatMonth(date: LocalDate): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).format(date)

/** The Activity hosting this Compose tree (needed for ads, consent and billing sheets). */
fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

/** Non-Compose version of [formatHm] for notifications and the widget. */
fun formatHmText(context: android.content.Context, d: Duration): String {
    val p = DurationParts.of(d)
    return context.getString(R.string.hours_minutes, p.hours, p.minutes)
}
