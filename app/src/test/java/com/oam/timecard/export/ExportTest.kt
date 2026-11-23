package com.oam.timecard.export

import com.oam.timecard.domain.engine.BERLIN
import com.oam.timecard.domain.engine.JobInfo
import com.oam.timecard.domain.engine.NEW_YORK
import com.oam.timecard.domain.engine.TimesheetBuilder
import com.oam.timecard.domain.engine.brk
import com.oam.timecard.domain.engine.d
import com.oam.timecard.domain.engine.hours
import com.oam.timecard.domain.engine.shift
import com.oam.timecard.domain.model.DayMarkEntry
import com.oam.timecard.domain.model.DayMarkType
import com.oam.timecard.domain.model.RulePresets
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

val TEST_LABELS = ExportLabels(
    title = "Timesheet", shiftsSheet = "Shifts", summarySheet = "Summary", date = "Date", job = "Job",
    start = "Start", end = "End", breakHours = "Breaks (h)", workedHours = "Worked (h)", type = "Type", note = "Note",
    running = "running", rate = "Rate", currency = "Currency", regular = "Regular (h)", overtime = "Overtime (h)",
    doubleTime = "Double time (h)", paidLeave = "Paid leave (h)", gross = "Gross pay", total = "Total",
    sick = "Sick", holiday = "Holiday", leave = "Paid leave", disclaimer = "Estimates only.",
)

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

/** Mon 16 – Sun 22 Nov 2026: a store job (USD, 5 x 9 h) and a café job (EUR, one shift + a sick day). */
fun sampleExport(): TimesheetExport {
    val store = JobInfo(1, RulePresets.US_FEDERAL, BigDecimal("20"))
    val cafe = JobInfo(2, RulePresets.GERMANY, BigDecimal("13.5"))
    val shifts = (0..4).map { shift("2026-11-${16 + it}", "08:00", 9.0, NEW_YORK, jobId = 1) } +
        shift("2026-11-21", "10:00", 6.5, BERLIN, jobId = 2,
            breaks = listOf(brk("2026-11-21T13:00", "2026-11-21T13:30", BERLIN))).copy(note = "Inventory, \"big\" day")
    val marks = listOf(DayMarkEntry(d("2026-11-19"), 2, DayMarkType.SICK, hours(4.0)))
    val ts = TimesheetBuilder.build(listOf(store, cafe), shifts, marks, DayOfWeek.MONDAY, d("2026-11-16"), d("2026-11-22"),
        Instant.parse("2030-01-01T00:00:00Z"))
    return TimesheetExportBuilder.build(
        listOf(ExportJob(1, "Store", "USD", BigDecimal("20")), ExportJob(2, "Café Müller", "EUR", BigDecimal("13.5"))),
        ts, TEST_LABELS,
    ) { i, z -> HHMM.format(i.atZone(z)) }
}

class ExportBuilderTest {
    @Test fun shiftsSheet_rowsSortedWithMarks() {
        val e = sampleExport()
        val body = e.shifts.body
        assertEquals(7, body.size) // 5 store shifts + 1 café shift + 1 sick day
        assertEquals(Cell.Text("Date"), e.shifts.header.first())
        val first = body.first()
        assertEquals(Cell.Date(d("2026-11-16")), first[0])
        assertEquals(Cell.Text("08:00"), first[2])
        assertEquals(Cell.Text("17:00"), first[3])
        assertEquals(Cell.Number(BigDecimal("9.00")), first[5])
        // Thursday: the store shift comes before the sick mark (marks sort last within a day).
        val thu = body.filter { it[0] == Cell.Date(d("2026-11-19")) }
        assertEquals(Cell.Text("Store"), thu[0][1])
        assertEquals(Cell.Text("Sick"), thu[1][6])
        assertEquals(Cell.Number(BigDecimal("4.00")), thu[1][5])
        val cafe = body.last()
        assertEquals(Cell.Number(BigDecimal("0.50")), cafe[4])
        assertEquals(Cell.Number(BigDecimal("6.00")), cafe[5])
        assertEquals(Cell.Text("Inventory, \"big\" day"), cafe[7])
    }

    @Test fun summarySheet_perJobAndPerCurrencyTotals() {
        val s = sampleExport().summary.body
        assertEquals(4, s.size) // 2 jobs + total USD + total EUR
        val store = s[0]
        assertEquals(Cell.Number(BigDecimal("40.00")), store[3])
        assertEquals(Cell.Number(BigDecimal("5.00")), store[4])
        assertEquals(Cell.Number(BigDecimal("950.00")), store[7])
        val cafe = s[1]
        assertEquals(Cell.Number(BigDecimal("4.00")), cafe[6]) // sick hours as paid leave
        assertEquals(Cell.Number(BigDecimal("135.00")), cafe[7]) // (6 + 4) h x 13.50
        assertEquals(Cell.Text("Total"), s[2][0])
        assertEquals(Cell.Text("USD"), s[2][2])
        assertEquals(Cell.Number(BigDecimal("950.00")), s[2][7])
    }

    @Test fun decimalHours() {
        assertEquals(BigDecimal("7.33"), TimesheetExportBuilder.hours(hours(7.0 + 20.0 / 60)))
        assertEquals(BigDecimal("0.00"), TimesheetExportBuilder.hours(java.time.Duration.ZERO))
    }

    @Test fun fileName() =
        assertEquals("Timesheet_2026-11-16_2026-11-22", TimesheetExportBuilder.fileBaseName("Time sheet!", d("2026-11-16"), d("2026-11-22")))
}

class CsvWriterTest {
    @Test fun englishCsv() {
        val csv = CsvWriter.toString(sampleExport())
        assertTrue(csv.startsWith("﻿Date,Job,Start,End,Breaks (h),Worked (h),Type,Note\r\n"))
        assertTrue(csv.contains("2026-11-16,Store,08:00,17:00,0.00,9.00,,\r\n"))
        assertTrue(csv.contains("\"Inventory, \"\"big\"\" day\""))
        assertTrue(csv.contains("\r\n\r\nJob,Rate,Currency,"))
        assertTrue(csv.contains("Store,20.00,USD,40.00,5.00,0.00,0.00,950.00\r\n"))
    }

    @Test fun germanCsv_semicolonAndDecimalComma() {
        val csv = CsvWriter.toString(sampleExport(), delimiter = ';', decimalComma = true)
        assertTrue(csv.contains("2026-11-16;Store;08:00;17:00;0,00;9,00;;\r\n"))
        assertTrue(csv.contains("Café Müller;13,50;EUR;"))
        // Comma inside text needs no quotes with ';' as delimiter, but quotes still do.
        assertTrue(csv.contains(";\"Inventory, \"\"big\"\" day\"\r\n"))
    }

    @Test fun formulaInjectionIsNeutralised() {
        assertEquals("'=HYPERLINK(1)", CsvWriter.quote("=HYPERLINK(1)", ','))
        assertEquals("\"'+1,2\"", CsvWriter.quote("+1,2", ','))
        assertEquals("plain", CsvWriter.quote("plain", ','))
    }
}

class XlsxWriterTest {
    private fun entries(bytes: ByteArray): Map<String, String> {
        val map = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                map[e.name] = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        return map
    }

    @Test fun packageHasAllParts() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sampleExport().sheets, out)
        val parts = entries(out.toByteArray())
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml",
                "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml"),
            parts.keys,
        )
        assertTrue(parts.getValue("xl/workbook.xml").contains("""<sheet name="Shifts" sheetId="1" r:id="rId1"/>"""))
        val sheet1 = parts.getValue("xl/worksheets/sheet1.xml")
        // 2026-11-16 is Excel serial 46342.
        assertTrue(sheet1.contains("""<c r="A2" s="1"><v>46342</v></c>"""))
        assertTrue(sheet1.contains("""<c r="F2" s="2"><v>9.00</v></c>"""))
        assertTrue(sheet1.contains("Inventory, &quot;big&quot; day"))
        assertTrue(parts.getValue("xl/worksheets/sheet2.xml").contains("Café Müller"))
    }

    @Test fun helpers() {
        assertEquals("A", XlsxWriter.columnName(0))
        assertEquals("Z", XlsxWriter.columnName(25))
        assertEquals("AA", XlsxWriter.columnName(26))
        assertEquals("AZ", XlsxWriter.columnName(51))
        assertEquals(listOf("A B", "Sheet2", "a b (2)"), XlsxWriter.uniqueSheetNames(listOf("A/B", "", "a b")))
        assertEquals("a&amp;b&lt;c", XlsxWriter.escape("a&b<c\u0001"))
        assertFalse(XlsxWriter.escape("x\u0000y").contains('\u0000'))
    }
}
