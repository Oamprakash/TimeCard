package com.oam.timecard.backup

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Everything the user entered, as plain values. Room rows are mapped to/from these in the data layer,
 * so this file (and its JSON) has no Android dependency. Money is stored as decimal strings, instants
 * as epoch milliseconds, dates as ISO text: nothing is lost in a round trip.
 */
data class Backup(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Instant,
    val settings: BackupSettings,
    val ruleSets: List<BackupRuleSet>,
    val jobs: List<BackupJob>,
    val shifts: List<BackupShift>,
    val breaks: List<BackupBreak>,
    val dayMarks: List<BackupDayMark>,
) {
    companion object {
        const val FORMAT = "timecard-backup"
        const val CURRENT_VERSION = 1
    }
}

data class BackupSettings(
    val weekStart: String,
    val payPeriodType: String,
    val payPeriodAnchor: LocalDate,
    val selectedJobId: Long?,
    val stillClockedInHours: Int?,
    val ruleWarnings: Boolean,
)

data class BackupRuleSet(
    val id: Long,
    val presetKind: String,
    val dailyOtAfterMin: Int?,
    val dailyDoubleAfterMin: Int?,
    val weeklyOtAfterMin: Int?,
    val otMultiplier: BigDecimal,
    val doubleMultiplier: BigDecimal,
    val autoBreakMin: Int,
    val autoBreakAfterMin: Int?,
    val maxDailyMin: Int?,
    val breakWarn1AfterMin: Int?,
    val breakWarn1Min: Int?,
    val breakWarn2AfterMin: Int?,
    val breakWarn2Min: Int?,
)

data class BackupJob(
    val id: Long,
    val name: String,
    val color: Int,
    val hourlyRate: BigDecimal,
    val currency: String,
    val ruleSetId: Long,
    val archived: Boolean,
    val sortOrder: Int,
    val scheduledMinPerWeek: Int?,
)

data class BackupShift(
    val id: Long,
    val jobId: Long,
    val start: Instant,
    val end: Instant?,
    val zoneId: String,
    val workDate: LocalDate,
    val note: String?,
    val edited: Boolean,
)

data class BackupBreak(val id: Long, val shiftId: Long, val start: Instant, val end: Instant?, val auto: Boolean)

data class BackupDayMark(val date: LocalDate, val jobId: Long, val type: String, val paidMinutes: Int)

class BackupFormatException(message: String) : IllegalArgumentException(message)

object BackupCodec {

    fun encode(b: Backup): String = Json.write(
        linkedMapOf(
            "format" to Backup.FORMAT,
            "version" to b.version,
            "exportedAt" to b.exportedAt.toEpochMilli(),
            "settings" to with(b.settings) {
                linkedMapOf(
                    "weekStart" to weekStart, "payPeriodType" to payPeriodType, "payPeriodAnchor" to payPeriodAnchor.toString(),
                    "selectedJobId" to selectedJobId, "stillClockedInHours" to stillClockedInHours, "ruleWarnings" to ruleWarnings,
                )
            },
            "ruleSets" to b.ruleSets.map {
                linkedMapOf(
                    "id" to it.id, "presetKind" to it.presetKind, "dailyOtAfterMin" to it.dailyOtAfterMin,
                    "dailyDoubleAfterMin" to it.dailyDoubleAfterMin, "weeklyOtAfterMin" to it.weeklyOtAfterMin,
                    "otMultiplier" to it.otMultiplier.toPlainString(), "doubleMultiplier" to it.doubleMultiplier.toPlainString(),
                    "autoBreakMin" to it.autoBreakMin, "autoBreakAfterMin" to it.autoBreakAfterMin, "maxDailyMin" to it.maxDailyMin,
                    "breakWarn1AfterMin" to it.breakWarn1AfterMin, "breakWarn1Min" to it.breakWarn1Min,
                    "breakWarn2AfterMin" to it.breakWarn2AfterMin, "breakWarn2Min" to it.breakWarn2Min,
                )
            },
            "jobs" to b.jobs.map {
                linkedMapOf(
                    "id" to it.id, "name" to it.name, "color" to it.color, "hourlyRate" to it.hourlyRate.toPlainString(),
                    "currency" to it.currency, "ruleSetId" to it.ruleSetId, "archived" to it.archived,
                    "sortOrder" to it.sortOrder, "scheduledMinPerWeek" to it.scheduledMinPerWeek,
                )
            },
            "shifts" to b.shifts.map {
                linkedMapOf(
                    "id" to it.id, "jobId" to it.jobId, "start" to it.start.toEpochMilli(), "end" to it.end?.toEpochMilli(),
                    "zoneId" to it.zoneId, "workDate" to it.workDate.toString(), "note" to it.note, "edited" to it.edited,
                )
            },
            "breaks" to b.breaks.map {
                linkedMapOf("id" to it.id, "shiftId" to it.shiftId, "start" to it.start.toEpochMilli(), "end" to it.end?.toEpochMilli(), "auto" to it.auto)
            },
            "dayMarks" to b.dayMarks.map {
                linkedMapOf("date" to it.date.toString(), "jobId" to it.jobId, "type" to it.type, "paidMinutes" to it.paidMinutes)
            },
        )
    )

    /** @throws BackupFormatException if this isn't a Time Card backup or it's from a newer app version. */
    fun decode(text: String): Backup {
        val root = try { Json.parse(text) } catch (e: Json.ParseException) {
            throw BackupFormatException("Not valid JSON: ${e.message}")
        } as? Map<*, *> ?: throw BackupFormatException("Not a backup file")
        if (root["format"] != Backup.FORMAT) throw BackupFormatException("Not a Time Card backup")
        val version = root.long("version").toInt()
        if (version > Backup.CURRENT_VERSION) throw BackupFormatException("Backup is from a newer version of the app")

        return try {
            val s = root.obj("settings")
            Backup(
                version = version,
                exportedAt = Instant.ofEpochMilli(root.long("exportedAt")),
                settings = BackupSettings(
                    weekStart = s.str("weekStart"),
                    payPeriodType = s.str("payPeriodType"),
                    payPeriodAnchor = LocalDate.parse(s.str("payPeriodAnchor")),
                    selectedJobId = s.longOrNull("selectedJobId"),
                    stillClockedInHours = s.longOrNull("stillClockedInHours")?.toInt(),
                    ruleWarnings = s["ruleWarnings"] as? Boolean ?: true,
                ),
                ruleSets = root.list("ruleSets").map {
                    BackupRuleSet(
                        id = it.long("id"), presetKind = it.str("presetKind"),
                        dailyOtAfterMin = it.intOrNull("dailyOtAfterMin"), dailyDoubleAfterMin = it.intOrNull("dailyDoubleAfterMin"),
                        weeklyOtAfterMin = it.intOrNull("weeklyOtAfterMin"),
                        otMultiplier = BigDecimal(it.str("otMultiplier")), doubleMultiplier = BigDecimal(it.str("doubleMultiplier")),
                        autoBreakMin = it.long("autoBreakMin").toInt(), autoBreakAfterMin = it.intOrNull("autoBreakAfterMin"),
                        maxDailyMin = it.intOrNull("maxDailyMin"),
                        breakWarn1AfterMin = it.intOrNull("breakWarn1AfterMin"), breakWarn1Min = it.intOrNull("breakWarn1Min"),
                        breakWarn2AfterMin = it.intOrNull("breakWarn2AfterMin"), breakWarn2Min = it.intOrNull("breakWarn2Min"),
                    )
                },
                jobs = root.list("jobs").map {
                    BackupJob(
                        id = it.long("id"), name = it.str("name"), color = it.long("color").toInt(),
                        hourlyRate = BigDecimal(it.str("hourlyRate")), currency = it.str("currency"),
                        ruleSetId = it.long("ruleSetId"), archived = it["archived"] as? Boolean ?: false,
                        sortOrder = it.long("sortOrder").toInt(), scheduledMinPerWeek = it.intOrNull("scheduledMinPerWeek"),
                    )
                },
                shifts = root.list("shifts").map {
                    BackupShift(
                        id = it.long("id"), jobId = it.long("jobId"), start = Instant.ofEpochMilli(it.long("start")),
                        end = it.longOrNull("end")?.let(Instant::ofEpochMilli), zoneId = it.str("zoneId"),
                        workDate = LocalDate.parse(it.str("workDate")), note = it["note"] as? String,
                        edited = it["edited"] as? Boolean ?: false,
                    )
                },
                breaks = root.list("breaks").map {
                    BackupBreak(
                        id = it.long("id"), shiftId = it.long("shiftId"), start = Instant.ofEpochMilli(it.long("start")),
                        end = it.longOrNull("end")?.let(Instant::ofEpochMilli), auto = it["auto"] as? Boolean ?: false,
                    )
                },
                dayMarks = root.list("dayMarks").map {
                    BackupDayMark(LocalDate.parse(it.str("date")), it.long("jobId"), it.str("type"), it.long("paidMinutes").toInt())
                },
            ).also(::checkReferences)
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("Backup is damaged: ${e.message}")
        }
    }

    /** Rejects files whose rows point at missing rows, so an import can never half-apply. */
    private fun checkReferences(b: Backup) {
        val rules = b.ruleSets.map { it.id }.toSet()
        val jobs = b.jobs.map { it.id }.toSet()
        val shifts = b.shifts.map { it.id }.toSet()
        if (b.jobs.any { it.ruleSetId !in rules }) throw BackupFormatException("A job refers to missing rules")
        if (b.shifts.any { it.jobId !in jobs }) throw BackupFormatException("A shift refers to a missing job")
        if (b.breaks.any { it.shiftId !in shifts }) throw BackupFormatException("A break refers to a missing shift")
        if (b.dayMarks.any { it.jobId !in jobs }) throw BackupFormatException("A day mark refers to a missing job")
        if (b.shifts.count { it.end == null } > 1) throw BackupFormatException("More than one running shift")
    }

    private fun Map<*, *>.obj(k: String) = this[k] as? Map<*, *> ?: throw BackupFormatException("Missing $k")
    private fun Map<*, *>.list(k: String): List<Map<*, *>> =
        (this[k] as? List<*> ?: throw BackupFormatException("Missing $k")).map { it as? Map<*, *> ?: throw BackupFormatException("Bad $k entry") }
    private fun Map<*, *>.str(k: String) = this[k] as? String ?: throw BackupFormatException("Missing $k")
    private fun Map<*, *>.long(k: String) = (this[k] as? Long) ?: throw BackupFormatException("Missing $k")
    private fun Map<*, *>.longOrNull(k: String) = this[k] as? Long
    private fun Map<*, *>.intOrNull(k: String) = (this[k] as? Long)?.toInt()
}
