package com.oamsapps.punchd.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Short symbols, so a US user abroad sees "$ 12.00", not "US$ 12.00" or "USD 12.00". */
private val NARROW_SYMBOLS = mapOf(
    "USD" to "$", "CAD" to "$", "AUD" to "$", "NZD" to "$", "SGD" to "$", "MXN" to "$", "HKD" to "$",
    "EUR" to "€", "GBP" to "£", "INR" to "₹", "JPY" to "¥", "CNY" to "¥", "KRW" to "₩",
    "TRY" to "₺", "PLN" to "zł", "BRL" to "R$", "PHP" to "₱", "NGN" to "₦", "ILS" to "₪", "VND" to "₫",
    "SEK" to "kr", "NOK" to "kr", "DKK" to "kr", "CHF" to "CHF",
)

private const val NBSP = ' '

/**
 * "$ 1,234.50", "₹ 19.64", "1.234,50 €": short symbol, separated by a no-break space, on the side
 * the locale puts it, with the currency's own number of decimals. The currency *code* goes in a row
 * label where it's needed (e.g. "USD" in the All jobs totals), never next to the symbol.
 */
fun formatMoneyNarrow(amount: BigDecimal, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    val symbol = NARROW_SYMBOLS[currencyCode] ?: currency?.getSymbol(locale) ?: currencyCode
    val digits = (currency?.defaultFractionDigits ?: 2).coerceAtLeast(0)
    val number = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
        roundingMode = RoundingMode.HALF_UP
    }.format(amount)
    return if (symbolAfterNumber(locale)) "$number$NBSP$symbol" else "$symbol$NBSP$number"
}

/** German, French, … write "12,00 €"; English writes "$12.00". */
internal fun symbolAfterNumber(locale: Locale): Boolean {
    val pattern = (NumberFormat.getCurrencyInstance(locale) as? DecimalFormat)?.toPattern() ?: return false
    val positive = pattern.substringBefore(';').trim()
    return positive.endsWith("¤") && !positive.startsWith("¤")
}
