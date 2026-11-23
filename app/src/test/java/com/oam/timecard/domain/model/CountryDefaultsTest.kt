package com.oam.timecard.domain.model

import com.oam.timecard.domain.engine.PayPeriodType
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CountryDefaultsTest {
    @Test fun presetsPerCountry() {
        assertEquals(PresetKind.US_FEDERAL, Countries.defaults(WorkCountry.US).preset)
        assertEquals(PresetKind.CALIFORNIA, Countries.defaults(WorkCountry.CALIFORNIA).preset)
        with(Countries.defaults(WorkCountry.GERMANY)) {
            assertEquals(PresetKind.GERMANY, preset)
            assertEquals("EUR", currency)
            assertEquals(DayOfWeek.MONDAY, weekStart)
            assertEquals(PayPeriodType.MONTHLY, payPeriod)
        }
        assertNull(Countries.defaults(WorkCountry.OTHER).currency)
    }

    @Test fun guessFromRegion() {
        assertEquals(WorkCountry.US, Countries.guess("us"))
        assertEquals(WorkCountry.GERMANY, Countries.guess("DE"))
        assertEquals(WorkCountry.OTHER, Countries.guess("IN"))
        assertEquals(WorkCountry.OTHER, Countries.guess(null))
    }
}

class HoursInputTest {
    @Test fun parses() {
        assertEquals(450, HoursInput.parseMinutes("7.5"))
        assertEquals(450, HoursInput.parseMinutes(" 7,5 "))
        assertEquals(2400, HoursInput.parseMinutes("40"))
        assertEquals(20, HoursInput.parseMinutes("0.33"))
        assertNull(HoursInput.parseMinutes(""))
    }

    @Test fun rejects() {
        assertThrows(IllegalArgumentException::class.java) { HoursInput.parseMinutes("abc") }
        assertThrows(IllegalArgumentException::class.java) { HoursInput.parseMinutes("-1") }
        assertThrows(IllegalArgumentException::class.java) { HoursInput.parseMinutes("25", maxHours = 24) }
    }

    @Test fun formats() {
        assertEquals("7.5", HoursInput.format(450))
        assertEquals("8", HoursInput.format(480))
        assertEquals("40", HoursInput.format(2400))
        assertEquals("", HoursInput.format(null))
    }
}
