package com.oamsapps.punchd.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.oamsapps.punchd.R
import com.oamsapps.punchd.data.db.toDomain
import com.oamsapps.punchd.data.db.toJobInfo
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.PayPeriod
import com.oamsapps.punchd.domain.engine.TimesheetBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
}

/** Builds a pay-period timesheet file in the app cache and returns a shareable content:// URI. */
@Singleton
class TimesheetExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: TimeCardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val dir get() = File(context.cacheDir, "exports")

    suspend fun export(period: PayPeriod, format: ExportFormat): Uri = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        val jobs = repo.activeJobs.first()
        val shifts = repo.observeShifts(HoursCalculator.loadFrom(period.start, s.weekStart), period.endInclusive).first()
        val marks = repo.observeMarks(period.start, period.endInclusive).first()
        val timesheet = TimesheetBuilder.build(
            jobs = jobs.map { it.toJobInfo() },
            shifts = shifts.map { it.toDomain() },
            marks = marks.map { it.toDomain() },
            weekStart = s.weekStart,
            from = period.start,
            to = period.endInclusive,
            now = clock.instant(),
        )
        val locale = Locale.getDefault()
        // Spreadsheets get 24 h "HH:mm" so they can be parsed; the PDF follows the device format.
        val timeFormat = if (format == ExportFormat.PDF) DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        else DateTimeFormatter.ofPattern("HH:mm")
        val export = TimesheetExportBuilder.build(
            jobs = jobs.map { ExportJob(it.job.id, it.job.name, it.job.currency, it.job.hourlyRate) },
            timesheet = timesheet,
            labels = labels(),
            time = { i, z -> timeFormat.format(i.atZone(z)) },
        )

        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // only the latest export is kept
        val file = File(dir, TimesheetExportBuilder.fileBaseName(context.getString(R.string.export_file_prefix), period.start, period.endInclusive) + "." + format.extension)
        file.outputStream().buffered().use { out ->
            when (format) {
                ExportFormat.CSV -> {
                    val decimalComma = DecimalFormatSymbols.getInstance(locale).decimalSeparator == ','
                    CsvWriter.write(export, out, delimiter = if (decimalComma) ';' else ',', decimalComma = decimalComma)
                }
                ExportFormat.XLSX -> XlsxWriter.write(export.sheets, out)
                ExportFormat.PDF -> {
                    val now = clock.instant().atZone(ZoneId.systemDefault())
                    val generated = context.getString(
                        R.string.export_generated_on,
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(now),
                    )
                    PdfTimesheetRenderer(locale) { context.getString(R.string.export_page, it) }.render(export, generated, out)
                }
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun shareIntent(uri: Uri, format: ExportFormat): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(format.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_title))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, context.getString(R.string.export_share_title))
    }

    private fun labels() = with(context) {
        ExportLabels(
            title = getString(R.string.export_title),
            shiftsSheet = getString(R.string.export_sheet_shifts),
            summarySheet = getString(R.string.export_sheet_summary),
            date = getString(R.string.date),
            job = getString(R.string.export_col_job),
            start = getString(R.string.start),
            end = getString(R.string.end),
            breakHours = getString(R.string.export_col_breaks),
            workedHours = getString(R.string.export_col_worked),
            type = getString(R.string.export_col_type),
            note = getString(R.string.export_col_note),
            running = getString(R.string.now),
            rate = getString(R.string.export_col_rate),
            currency = getString(R.string.export_col_currency),
            regular = getString(R.string.export_col_regular),
            overtime = getString(R.string.export_col_overtime),
            doubleTime = getString(R.string.export_col_double),
            paidLeave = getString(R.string.export_col_leave),
            gross = getString(R.string.gross_pay_estimate),
            total = getString(R.string.export_total),
            sick = getString(R.string.mark_sick),
            holiday = getString(R.string.mark_holiday),
            leave = getString(R.string.mark_paid_leave),
            disclaimer = getString(R.string.pay_disclaimer),
        )
    }
}
