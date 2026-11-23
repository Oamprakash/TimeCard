package com.oamsapps.punchd.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.oamsapps.punchd.R
import com.oamsapps.punchd.backup.Backup
import com.oamsapps.punchd.backup.BackupBreak
import com.oamsapps.punchd.backup.BackupCodec
import com.oamsapps.punchd.backup.BackupDayMark
import com.oamsapps.punchd.backup.BackupFormatException
import com.oamsapps.punchd.backup.BackupJob
import com.oamsapps.punchd.backup.BackupRuleSet
import com.oamsapps.punchd.backup.BackupSettings
import com.oamsapps.punchd.backup.BackupShift
import com.oamsapps.punchd.clock.ClockController
import com.oamsapps.punchd.data.db.BreakEntity
import com.oamsapps.punchd.data.db.DayMarkEntity
import com.oamsapps.punchd.data.db.JobEntity
import com.oamsapps.punchd.data.db.RuleSetEntity
import com.oamsapps.punchd.data.db.ShiftEntity
import com.oamsapps.punchd.data.db.TimeCardDatabase
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.domain.engine.PayPeriodType
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.domain.model.PresetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class ImportSummary(val jobs: Int, val shifts: Int)

/**
 * Whole-database backup as one JSON file (spec: instead of accounts / cloud sync).
 * Export goes through the share sheet (Drive, email, Files); import replaces all data in one
 * transaction, so a bad file can never leave the app half-restored.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TimeCardDatabase,
    private val settings: SettingsRepository,
    private val controller: ClockController,
    private val clock: Clock,
) {
    suspend fun exportShareIntent(): Intent = withContext(Dispatchers.IO) {
        val json = BackupCodec.encode(snapshot())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val today = clock.instant().atZone(ZoneId.systemDefault()).toLocalDate()
        val file = File(dir, "punchd-backup-$today.json").apply { writeText(json, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.backup_file_subject))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, uri)
        Intent.createChooser(send, context.getString(R.string.backup_share_title))
    }

    /** @throws BackupFormatException for files that aren't a valid backup (nothing is changed then). */
    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupFormatException("Can't read the file")
        if (text.length > 50_000_000) throw BackupFormatException("File is too large")
        val b = BackupCodec.decode(text)
        restore(b)
        controller.sync() // running shift, notification, reminders and widget follow the restored data
        ImportSummary(b.jobs.size, b.shifts.size)
    }

    private suspend fun snapshot(): Backup {
        val s = settings.settings.first()
        return Backup(
            exportedAt = clock.instant(),
            settings = BackupSettings(
                weekStart = s.weekStart.name,
                payPeriodType = s.payPeriod.type.name,
                payPeriodAnchor = s.payPeriod.anchor,
                selectedJobId = s.selectedJobId,
                stillClockedInHours = s.stillClockedInHours,
                ruleWarnings = s.ruleWarnings,
            ),
            ruleSets = db.ruleSetDao().getAll().map {
                BackupRuleSet(
                    it.id, it.presetKind.name, it.dailyOtAfterMin, it.dailyDoubleAfterMin, it.weeklyOtAfterMin,
                    it.otMultiplier, it.doubleMultiplier, it.autoBreakMin, it.autoBreakAfterMin, it.maxDailyMin,
                    it.breakWarn1AfterMin, it.breakWarn1Min, it.breakWarn2AfterMin, it.breakWarn2Min,
                )
            },
            jobs = db.jobDao().getAll().map {
                BackupJob(it.id, it.name, it.color, it.hourlyRate, it.currency, it.ruleSetId, it.archived, it.sortOrder, it.scheduledMinPerWeek)
            },
            shifts = db.shiftDao().getAllShifts().map {
                BackupShift(it.id, it.jobId, it.startUtc, it.endUtc, it.zoneId.id, it.workDate, it.note, it.edited)
            },
            breaks = db.shiftDao().getAllBreaks().map { BackupBreak(it.id, it.shiftId, it.startUtc, it.endUtc, it.isAutoDeducted) },
            dayMarks = db.dayMarkDao().getAll().map { BackupDayMark(it.date, it.jobId, it.type.name, it.paidMinutes) },
        )
    }

    private suspend fun restore(b: Backup) {
        // Convert everything first: an unknown enum or zone fails here, before any data is touched.
        val ruleSets = b.ruleSets.map {
            RuleSetEntity(
                it.id, enumOf<PresetKind>(it.presetKind), it.dailyOtAfterMin, it.dailyDoubleAfterMin, it.weeklyOtAfterMin,
                it.otMultiplier, it.doubleMultiplier, it.autoBreakMin, it.autoBreakAfterMin, it.maxDailyMin,
                it.breakWarn1AfterMin, it.breakWarn1Min, it.breakWarn2AfterMin, it.breakWarn2Min,
            )
        }
        val jobs = b.jobs.map {
            JobEntity(it.id, it.name, it.color, it.hourlyRate, it.currency, it.ruleSetId, it.archived, it.sortOrder, it.scheduledMinPerWeek)
        }
        val shifts = b.shifts.map {
            val zone = runCatching { ZoneId.of(it.zoneId) }.getOrElse { throw BackupFormatException("Unknown time zone ${it.zoneId}") }
            ShiftEntity(it.id, it.jobId, it.start, it.end, zone, it.workDate, it.note, it.edited)
        }
        val breaks = b.breaks.map { BreakEntity(it.id, it.shiftId, it.start, it.end, it.auto) }
        val marks = b.dayMarks.map { DayMarkEntity(it.date, it.jobId, enumOf<DayMarkType>(it.type), it.paidMinutes) }
        val weekStart = enumOf<DayOfWeek>(b.settings.weekStart)
        val payType = enumOf<PayPeriodType>(b.settings.payPeriodType)

        db.withTransaction {
            db.shiftDao().deleteAllBreaks()
            db.dayMarkDao().deleteAll()
            db.shiftDao().deleteAllShifts()
            db.jobDao().deleteAll()
            db.ruleSetDao().deleteAll()
            db.ruleSetDao().insertAll(ruleSets)
            db.jobDao().insertAll(jobs)
            db.shiftDao().insertAllShifts(shifts)
            db.shiftDao().insertAllBreaks(breaks)
            db.dayMarkDao().insertAll(marks)
        }
        settings.setWeekStart(weekStart)
        settings.setPayPeriod(PayPeriodConfig(payType, b.settings.payPeriodAnchor))
        settings.setReminders(b.settings.stillClockedInHours, b.settings.ruleWarnings)
        b.settings.selectedJobId?.let { settings.setSelectedJob(it) } ?: settings.clearSelectedJob()
        settings.setOnboardingDone()
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String): E =
        runCatching { enumValueOf<E>(name) }.getOrElse { throw BackupFormatException("Unknown value $name") }
}
