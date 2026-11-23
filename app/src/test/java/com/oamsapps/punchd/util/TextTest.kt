package com.oamsapps.punchd.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTest {
    @Test fun sentenceCase() {
        assertEquals("Test job", "test job".sentenceCase(Locale.ENGLISH))
        assertEquals("Primary job", "  primary job ".sentenceCase(Locale.ENGLISH))
        assertEquals("IKEA", "IKEA".sentenceCase(Locale.ENGLISH))
        assertEquals("McDonald's", "mcDonald's".sentenceCase(Locale.ENGLISH))
        assertEquals("Ärztehaus", "ärztehaus".sentenceCase(Locale.GERMAN))
        assertEquals("24/7 Store", "24/7 Store".sentenceCase(Locale.ENGLISH))
        assertEquals("", "".sentenceCase(Locale.ENGLISH))
    }
}
