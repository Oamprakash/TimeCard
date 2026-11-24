package com.oamsapps.punchd.util

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    private val nb = ' '
    private fun m(v: String, cur: String, loc: Locale) = formatMoneyNarrow(BigDecimal(v), cur, loc)

    @Test fun usDollarsInTheUs() = assertEquals("$$nb" + "1,234.50", m("1234.5", "USD", Locale.US))
    @Test fun dollarsOnAnIndianPhone_noUsPrefix() = assertEquals("$$nb" + "0.00", m("0", "USD", Locale.forLanguageTag("en-IN")))
    @Test fun rupees() = assertEquals("₹${nb}19.64", m("19.64", "INR", Locale.US))
    @Test fun euroInGermany_symbolAfter() = assertEquals("1.234,50$nb€", m("1234.5", "EUR", Locale.GERMANY))
    @Test fun yenHasNoDecimals() = assertEquals("¥${nb}1,200", m("1199.6", "JPY", Locale.US))
    @Test fun unknownCodeFallsBackToCode() = assertEquals("XYZ${nb}5.00", m("5", "XYZ", Locale.US))
    @Test fun roundsHalfUp() = assertEquals("$$nb" + "0.13", m("0.125", "USD", Locale.US))
}
