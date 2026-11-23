package com.oamsapps.punchd.backup

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {
    @Test fun roundTrip() {
        val v = linkedMapOf(
            "s" to "Café \"Müller\"\nline\t\u0001", "n" to null, "t" to true, "l" to -42L,
            "d" to 1.5, "list" to listOf(1L, "x", emptyMap<String, Any?>(), emptyList<Any?>()),
        )
        assertEquals(v, Json.parse(Json.write(v)))
    }

    @Test fun parsesWhitespaceAndUnicodeEscapes() {
        assertEquals(mapOf("a" to listOf(1L, 2L), "é" to "é"), Json.parse(""" { "a" : [ 1 , 2 ] , "é" : "é" } """))
    }

    @Test fun rejectsBrokenJson() {
        listOf("{", """{"a":}""", "[1,]", """{"a":1} x""", "\"unterminated").forEach {
            assertThrows(Json.ParseException::class.java) { Json.parse(it) }
        }
    }
}

class BackupCodecTest {
    private val sample = Backup(
        exportedAt = Instant.parse("2026-11-23T12:00:00Z"),
        settings = BackupSettings("MONDAY", "BIWEEKLY", LocalDate.parse("2026-01-05"), 7, 10, true),
        ruleSets = listOf(BackupRuleSet(3, "CALIFORNIA", 480, 720, 2400, BigDecimal("1.5"), BigDecimal("2.0"), 30, 360, null, null, null, null, null)),
        jobs = listOf(BackupJob(7, "Store", 0xFF1E88E5.toInt(), BigDecimal("17.33"), "USD", 3, false, 0, 2400)),
        shifts = listOf(
            BackupShift(11, 7, Instant.parse("2026-11-20T16:00:00Z"), Instant.parse("2026-11-21T01:00:00Z"), "America/Los_Angeles",
                LocalDate.parse("2026-11-20"), "Night, \"inventory\"", true),
            BackupShift(12, 7, Instant.parse("2026-11-23T08:00:00Z"), null, "America/Los_Angeles", LocalDate.parse("2026-11-23"), null, false),
        ),
        breaks = listOf(BackupBreak(21, 11, Instant.parse("2026-11-20T20:00:00Z"), Instant.parse("2026-11-20T20:30:00Z"), true)),
        dayMarks = listOf(BackupDayMark(LocalDate.parse("2026-11-19"), 7, "SICK", 480)),
    )

    @Test fun roundTripKeepsEverythingExact() {
        val json = BackupCodec.encode(sample)
        assertTrue(json.startsWith("""{"format":"punchd-backup","version":1,"""))
        val back = BackupCodec.decode(json)
        assertEquals(sample, back)
        assertEquals(BigDecimal("17.33"), back.jobs.single().hourlyRate)
        assertNull(back.shifts[1].end)
        assertEquals(0xFF1E88E5.toInt(), back.jobs.single().color) // negative ARGB int survives
    }

    @Test fun rejectsOtherFiles() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("""{"hello":1}""") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("[]") }
    }

    @Test fun rejectsNewerVersion() {
        val json = BackupCodec.encode(sample).replace("\"version\":1", "\"version\":2")
        val e = assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json) }
        assertTrue(e.message!!.contains("newer"))
    }

    @Test fun rejectsBrokenReferences() {
        val json = BackupCodec.encode(sample.copy(breaks = listOf(sample.breaks[0].copy(shiftId = 999))))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json) }
    }

    @Test fun rejectsDamagedValues() {
        val json = BackupCodec.encode(sample).replace("\"17.33\"", "\"abc\"")
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json) }
    }
}
