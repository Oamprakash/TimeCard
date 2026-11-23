package com.oam.timecard.export

import com.oam.timecard.domain.engine.Timesheet
import com.oam.timecard.domain.model.DayMarkType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A spreadsheet-like cell. Each writer (CSV, XLSX, PDF) renders these its own way. */
sealed interface Cell {
    data class Text(val value: String) : Cell
    /** Hours or money; written as a real number so spreadsheets can sum it. */
    data class Number(val value: BigDecimal) : Cell
    data class Date(val value: LocalDate) : Cell
    data object Empty : Cell
}

/** First row is the header. */
data class Sheet(val name: String, val rows: List<List<Cell>>) {
    val header: List<Cell> get() = rows.firstOrNull().orEmpty()
    val body: List<List<Cell>> get() = rows.drop(1)
}

data class TimesheetExport(
    val title: String,
    val from: LocalDate,
    val to: LocalDate,
    val shifts: Sheet,
    val summary: Sheet,
    val disclaimer: String,
) {
    val sheets: List<Sheet> get() = listOf(shifts, summary)
}

/** Translated labels, so the export follows the app language (EN/DE). */
data class ExportLabels(
    val title: String,
    val shiftsSheet: String,
    val summarySheet: String,
    val date: String,
    val job: String,
    val start: String,
    val end: String,
    val breakHours: String,
    val workedHours: String,
    val type: String,
    val note: String,
    val running: String,
    val rate: String,
    val currency: String,
    val regular: String,
    val overtime: String,
    val doubleTime: String,
    val paidLeave: String,
    val gross: String,
    val total: String,
    val sick: String,
    val holiday: String,
    val leave: String,
    val disclaimer: String,
)

data class ExportJob(val id: Long, val name: String, val currency: String, val hourlyRate: BigDecimal)

object TimesheetExportBuilder {

    /**
     * Shifts sheet: one row per shift, then one per sick/holiday/leave mark, sorted by date.
     * Summary sheet: one row per job, then a total row per currency.
     * Hours are decimal (7.50 = 7 h 30 min), which is what payroll and spreadsheets expect.
     *
     * @param time formats a shift time in the zone it was recorded in (24 h for CSV/XLSX, localized for PDF).
     */
    fun build(
        jobs: List<ExportJob>,
        timesheet: Timesheet,
        labels: ExportLabels,
        time: (Instant, ZoneId) -> String,
    ): TimesheetExport {
        val jobsById = jobs.associateBy { it.id }

        data class Row(val date: LocalDate, val order: Instant?, val cells: List<Cell>)

        val shiftRows = timesheet.days.flatMap { day ->
            day.shifts.mapNotNull { r ->
                val job = jobsById[r.shift.jobId] ?: return@mapNotNull null
                Row(
                    day.date, r.shift.start,
                    listOf(
                        Cell.Date(day.date),
                        Cell.Text(job.name),
                        Cell.Text(time(r.shift.start, r.shift.zone)),
                        r.shift.end?.let { Cell.Text(time(it, r.shift.zone)) } ?: Cell.Text(labels.running),
                        Cell.Number(hours(r.breaks)),
                        Cell.Number(hours(r.worked)),
                        Cell.Empty,
                        r.shift.note?.let { Cell.Text(it) } ?: Cell.Empty,
                    ),
                )
            } + day.marks.mapNotNull { m ->
                val job = jobsById[m.jobId] ?: return@mapNotNull null
                Row(
                    day.date, null,
                    listOf(
                        Cell.Date(day.date), Cell.Text(job.name), Cell.Empty, Cell.Empty, Cell.Empty,
                        Cell.Number(hours(m.paidTime)), Cell.Text(markLabel(m.type, labels)), Cell.Empty,
                    ),
                )
            }
        }.sortedWith(compareBy<Row> { it.date }.thenBy(nullsLast()) { it.order })

        val shifts = Sheet(
            labels.shiftsSheet,
            listOf(listOf(labels.date, labels.job, labels.start, labels.end, labels.breakHours, labels.workedHours, labels.type, labels.note).map(Cell::Text)) +
                shiftRows.map { it.cells },
        )

        val jobRows = timesheet.perJob.mapNotNull { s ->
            val job = jobsById[s.jobId] ?: return@mapNotNull null
            job to listOf(
                Cell.Text(job.name),
                Cell.Number(job.hourlyRate.setScale(2, RoundingMode.HALF_UP)),
                Cell.Text(job.currency),
                Cell.Number(hours(s.regular)),
                Cell.Number(hours(s.overtime)),
                Cell.Number(hours(s.doubleTime)),
                Cell.Number(hours(s.paidLeave)),
                Cell.Number(s.grossPay),
            )
        }
        val totalRows = jobRows.groupBy { it.first.currency }.map { (currency, rows) ->
            fun sum(i: Int) = rows.fold(BigDecimal.ZERO) { a, (_, cells) -> a + (cells[i] as Cell.Number).value }
            listOf(
                Cell.Text(labels.total), Cell.Empty, Cell.Text(currency),
                Cell.Number(sum(3)), Cell.Number(sum(4)), Cell.Number(sum(5)), Cell.Number(sum(6)), Cell.Number(sum(7)),
            )
        }
        val summary = Sheet(
            labels.summarySheet,
            listOf(listOf(labels.job, labels.rate, labels.currency, labels.regular, labels.overtime, labels.doubleTime, labels.paidLeave, labels.gross).map(Cell::Text)) +
                jobRows.map { it.second } + totalRows,
        )

        return TimesheetExport(labels.title, timesheet.from, timesheet.to, shifts, summary, labels.disclaimer)
    }

    /** Decimal hours rounded to 2 places: 7 h 20 min -> 7.33. */
    fun hours(d: Duration): BigDecimal =
        BigDecimal.valueOf(d.seconds).divide(BigDecimal(3600), 2, RoundingMode.HALF_UP)

    private fun markLabel(type: DayMarkType, l: ExportLabels) = when (type) {
        DayMarkType.SICK -> l.sick
        DayMarkType.HOLIDAY -> l.holiday
        DayMarkType.PAID_LEAVE -> l.leave
    }

    /** "Timesheet_2026-11-16_2026-11-30" — safe on every file system. */
    fun fileBaseName(prefix: String, from: LocalDate, to: LocalDate): String =
        prefix.replace(Regex("[^A-Za-z0-9_-]"), "").ifEmpty { "Timesheet" } + "_${from}_$to"
}
