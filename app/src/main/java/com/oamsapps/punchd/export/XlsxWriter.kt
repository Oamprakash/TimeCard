package com.oamsapps.punchd.export

import java.io.OutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free .xlsx (Office Open XML) writer: one worksheet per [Sheet], inline strings,
 * real numbers ("0.00") and real dates (Excel date format), bold header row with a frozen pane.
 * Opens in Excel, Google Sheets, LibreOffice and Numbers.
 */
object XlsxWriter {

    private const val STYLE_DEFAULT = 0
    private const val STYLE_DATE = 1
    private const val STYLE_NUMBER = 2
    private const val STYLE_HEADER = 3

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    fun write(sheets: List<Sheet>, out: OutputStream) {
        val names = uniqueSheetNames(sheets.map { it.name })
        ZipOutputStream(out).use { zip ->
            fun put(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", workbook(names))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", worksheet(s)) }
        }
    }

    internal fun worksheet(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (sheet.rows.size > 1) {
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        }
        val widths = columnWidths(sheet)
        if (widths.isNotEmpty()) {
            append("<cols>")
            widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = columnName(c) + (r + 1)
                val header = r == 0
                when (cell) {
                    is Cell.Text -> append("""<c r="$ref" t="inlineStr"${if (header) " s=\"$STYLE_HEADER\"" else ""}><is><t xml:space="preserve">${escape(cell.value)}</t></is></c>""")
                    is Cell.Number -> append("""<c r="$ref" s="$STYLE_NUMBER"><v>${cell.value.toPlainString()}</v></c>""")
                    is Cell.Date -> append("""<c r="$ref" s="$STYLE_DATE"><v>${ChronoUnit.DAYS.between(EXCEL_EPOCH, cell.value)}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** A, B, ... Z, AA, AB ... */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** Excel: max 31 chars, none of []:*?/\ and unique (case-insensitive). */
    internal fun uniqueSheetNames(names: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return names.mapIndexed { i, raw ->
            val base = raw.replace(Regex("""[\[\]:*?/\\]"""), " ").trim().take(31).ifEmpty { "Sheet${i + 1}" }
            var name = base
            var k = 2
            while (!used.add(name.lowercase())) {
                val suffix = " ($k)"
                name = base.take(31 - suffix.length) + suffix
                k++
            }
            name
        }
    }

    /** Escapes XML and drops characters XML 1.0 can't hold (control chars from pasted notes). */
    internal fun escape(s: String): String = buildString(s.length) {
        for (ch in s) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                else -> append(ch)
            }
        }
    }

    private fun columnWidths(sheet: Sheet): List<Int> {
        val cols = sheet.rows.maxOfOrNull { it.size } ?: 0
        return (0 until cols).map { c ->
            val longest = sheet.rows.maxOf { row ->
                when (val cell = row.getOrNull(c)) {
                    is Cell.Text -> cell.value.length
                    is Cell.Number -> cell.value.toPlainString().length
                    is Cell.Date -> 10
                    else -> 0
                }
            }
            (longest + 2).coerceIn(8, 50)
        }
    }

    private fun contentTypes(sheetCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { i, n -> append("""<sheet name="${escape(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheetCount: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    // cellXfs index: 0 default, 1 date (built-in format 14), 2 number "0.00" (built-in 2), 3 bold header.
    private const val STYLES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
            """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="4">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
            """<xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
            "</cellXfs>" +
            """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
            "</styleSheet>"
}
