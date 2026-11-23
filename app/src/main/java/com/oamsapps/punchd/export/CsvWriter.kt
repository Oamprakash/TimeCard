package com.oamsapps.punchd.export

import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * RFC 4180 CSV. For locales that write decimals with a comma (German), use [delimiter] ';' and
 * [decimalComma] = true so Excel opens the file with columns and numbers intact.
 * Starts with a UTF-8 BOM so Excel shows umlauts correctly. Dates are ISO (2026-11-23).
 */
object CsvWriter {

    fun write(export: TimesheetExport, out: OutputStream, delimiter: Char = ',', decimalComma: Boolean = false) {
        val w = OutputStreamWriter(out, Charsets.UTF_8)
        w.write("﻿")
        export.sheets.forEachIndexed { i, sheet ->
            if (i > 0) w.write("\r\n")
            sheet.rows.forEach { row ->
                w.write(row.joinToString(delimiter.toString()) { cell(it, delimiter, decimalComma) })
                w.write("\r\n")
            }
        }
        w.flush()
    }

    fun toString(export: TimesheetExport, delimiter: Char = ',', decimalComma: Boolean = false): String {
        val bytes = java.io.ByteArrayOutputStream()
        write(export, bytes, delimiter, decimalComma)
        return bytes.toString(Charsets.UTF_8.name())
    }

    internal fun cell(c: Cell, delimiter: Char, decimalComma: Boolean): String = when (c) {
        is Cell.Text -> quote(c.value, delimiter)
        is Cell.Number -> c.value.toPlainString().let { if (decimalComma) it.replace('.', ',') else it }
            .let { if (decimalComma && delimiter == ',') "\"$it\"" else it }
        is Cell.Date -> c.value.toString()
        Cell.Empty -> ""
    }

    /** Quotes when needed. A leading = + - @ is prefixed with ' so a spreadsheet never runs it as a formula. */
    internal fun quote(value: String, delimiter: Char): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needs = safe.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }
        return if (needs) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
