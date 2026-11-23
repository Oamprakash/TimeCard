package com.oamsapps.punchd.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Renders a [TimesheetExport] as an A4 PDF with Android's PdfDocument: title and period, the shifts
 * table (header repeated on every page), the summary table and the disclaimer. No library needed.
 */
class PdfTimesheetRenderer(
    private val locale: Locale = Locale.getDefault(),
    private val pageLabel: (Int) -> String = { "$it" },
) {
    private val pageW = 595 // A4 in points
    private val pageH = 842
    private val margin = 36f
    private val rowH = 16f
    private val bottom = pageH - margin - 18f // room for the footer

    private val title = paint(18f, bold = true)
    private val subtitle = paint(11f)
    private val small = paint(8f, color = Color.DKGRAY)
    private val cellPaint = paint(9f)
    private val headPaint = paint(9f, bold = true)
    private val headFill = Paint().apply { color = Color.rgb(235, 238, 242) }
    private val zebraFill = Paint().apply { color = Color.rgb(248, 249, 250) }
    private val line = Paint().apply { color = Color.rgb(200, 204, 210); strokeWidth = 0.5f }

    private val numbers = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    private val longDates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

    // Relative column widths.
    private val shiftCols = floatArrayOf(1.15f, 1.6f, 0.8f, 0.8f, 0.85f, 0.85f, 0.9f, 2.0f)
    private val summaryCols = floatArrayOf(1.8f, 0.8f, 0.8f, 1f, 1f, 1f, 1f, 1.1f)

    fun render(export: TimesheetExport, generatedOn: String, out: OutputStream) {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun finishPage() {
            val p = page ?: return
            p.canvas.drawText(pageLabel(pageNo), pageW - margin, pageH - margin, small.apply { textAlign = Paint.Align.RIGHT })
            small.textAlign = Paint.Align.LEFT
            doc.finishPage(p)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page!!.canvas
            y = margin
        }

        fun table(sheet: Sheet, weights: FloatArray, heading: String) {
            val widths = scale(weights)
            fun header() {
                canvas!!.drawRect(margin, y, pageW - margin, y + rowH, headFill)
                drawRow(canvas!!, sheet.header, widths, y, headPaint)
                y += rowH
            }
            if (y + rowH * 3 > bottom) newPage()
            canvas!!.drawText(heading, margin, y + 12f, subtitle.apply { typeface = Typeface.DEFAULT_BOLD })
            subtitle.typeface = Typeface.DEFAULT
            y += 20f
            header()
            sheet.body.forEachIndexed { i, row ->
                if (y + rowH > bottom) { newPage(); header() }
                if (i % 2 == 1) canvas!!.drawRect(margin, y, pageW - margin, y + rowH, zebraFill)
                drawRow(canvas!!, row, widths, y, cellPaint)
                y += rowH
                canvas!!.drawLine(margin, y, pageW - margin, y, line)
            }
            y += 18f
        }

        newPage()
        canvas!!.drawText(export.title, margin, y + 18f, title)
        y += 30f
        canvas!!.drawText("${longDates.format(export.from)} – ${longDates.format(export.to)}", margin, y + 11f, subtitle)
        y += 16f
        canvas!!.drawText(generatedOn, margin, y + 8f, small)
        y += 22f

        table(export.shifts, shiftCols, export.shifts.name)
        table(export.summary, summaryCols, export.summary.name)

        if (y + 20f > bottom) newPage()
        drawWrapped(canvas!!, export.disclaimer, margin, y + 8f, pageW - 2 * margin, small)

        finishPage()
        doc.writeTo(out)
        doc.close()
    }

    private fun drawRow(canvas: Canvas, row: List<Cell>, widths: FloatArray, top: Float, p: Paint) {
        var x = margin
        widths.forEachIndexed { i, w ->
            val cell = row.getOrNull(i) ?: Cell.Empty
            val text = format(cell)
            val numeric = cell is Cell.Number
            val fitted = ellipsize(text, w - 6f, p)
            if (numeric) {
                p.textAlign = Paint.Align.RIGHT
                canvas.drawText(fitted, x + w - 3f, top + 11.5f, p)
                p.textAlign = Paint.Align.LEFT
            } else {
                canvas.drawText(fitted, x + 3f, top + 11.5f, p)
            }
            x += w
        }
    }

    private fun format(c: Cell): String = when (c) {
        is Cell.Text -> c.value
        is Cell.Number -> numbers.format(c.value)
        is Cell.Date -> dates.format(c.value)
        Cell.Empty -> ""
    }

    private fun ellipsize(text: String, maxWidth: Float, p: Paint): String {
        if (p.measureText(text) <= maxWidth) return text
        val n = p.breakText(text, true, maxWidth - p.measureText("…"), null)
        return text.take(n.coerceAtLeast(0)) + "…"
    }

    private fun drawWrapped(canvas: Canvas, text: String, x: Float, y: Float, width: Float, p: Paint) {
        var lineY = y
        var rest = text
        while (rest.isNotEmpty()) {
            var n = p.breakText(rest, true, width, null)
            if (n < rest.length) rest.lastIndexOf(' ', n).takeIf { it > 0 }?.let { n = it }
            canvas.drawText(rest.substring(0, n).trim(), x, lineY, p)
            rest = rest.substring(n).trimStart()
            lineY += p.textSize * 1.4f
        }
    }

    private fun scale(weights: FloatArray): FloatArray {
        val total = weights.sum()
        val usable = pageW - 2 * margin
        return FloatArray(weights.size) { weights[it] / total * usable }
    }

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
