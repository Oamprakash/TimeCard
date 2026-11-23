package com.oam.timecard.domain.model

import com.oam.timecard.domain.engine.PayPeriodType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek

/** The country picked in onboarding: decides the rule preset and sensible defaults. */
enum class WorkCountry { US, CALIFORNIA, GERMANY, OTHER }

data class CountryDefaults(
    val preset: PresetKind,
    /** Null = use the device locale's currency. */
    val currency: String?,
    /** Null = use the device locale's first day of week. */
    val weekStart: DayOfWeek?,
    val payPeriod: PayPeriodType,
)

object Countries {
    fun defaults(country: WorkCountry): CountryDefaults = when (country) {
        WorkCountry.US -> CountryDefaults(PresetKind.US_FEDERAL, "USD", DayOfWeek.SUNDAY, PayPeriodType.BIWEEKLY)
        WorkCountry.CALIFORNIA -> CountryDefaults(PresetKind.CALIFORNIA, "USD", DayOfWeek.SUNDAY, PayPeriodType.BIWEEKLY)
        WorkCountry.GERMANY -> CountryDefaults(PresetKind.GERMANY, "EUR", DayOfWeek.MONDAY, PayPeriodType.MONTHLY)
        WorkCountry.OTHER -> CountryDefaults(PresetKind.CUSTOM, null, null, PayPeriodType.MONTHLY)
    }

    /** First guess from the device region (ISO 3166 code). California can't be told apart, so US -> US federal. */
    fun guess(regionCode: String?): WorkCountry = when (regionCode?.uppercase()) {
        "US" -> WorkCountry.US
        "DE" -> WorkCountry.GERMANY
        else -> WorkCountry.OTHER
    }
}

/** Parses hours typed by the user ("7.5", "7,5", "8") into whole minutes. */
object HoursInput {
    /** Blank -> null (rule off). Invalid, negative or over [maxHours] -> throws [IllegalArgumentException]. */
    fun parseMinutes(text: String, maxHours: Int = 168): Int? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        val hours = t.toBigDecimalOrNull() ?: throw IllegalArgumentException("Not a number: $text")
        require(hours.signum() >= 0 && hours <= BigDecimal(maxHours)) { "Out of range: $text" }
        return hours.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toInt()
    }

    /** 450 -> "7.5", 480 -> "8", null -> "". */
    fun format(minutes: Int?): String =
        minutes?.let { BigDecimal(it).divide(BigDecimal(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() }.orEmpty()
}
