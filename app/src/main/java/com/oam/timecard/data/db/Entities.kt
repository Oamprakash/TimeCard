package com.oam.timecard.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.oam.timecard.domain.model.DayMarkType
import com.oam.timecard.domain.model.PresetKind
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A job's own, editable copy of a rule preset. Built-in presets live in code (RulePresets);
 * creating a job copies one here, so editing a job's rules never affects another job.
 * All thresholds are stored in whole minutes; null = rule off.
 */
@Entity(tableName = "rule_sets")
data class RuleSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetKind: PresetKind,
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

@Entity(
    tableName = "jobs",
    foreignKeys = [ForeignKey(
        entity = RuleSetEntity::class, parentColumns = ["id"], childColumns = ["ruleSetId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("ruleSetId")],
)
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,            // ARGB
    val hourlyRate: BigDecimal,
    val currency: String,      // ISO 4217, e.g. "USD", "EUR"
    val ruleSetId: Long,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "shifts",
    foreignKeys = [ForeignKey(
        entity = JobEntity::class, parentColumns = ["id"], childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["jobId", "workDate"]), Index("endUtc")],
)
data class ShiftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val startUtc: Instant,
    val endUtc: Instant?,      // null = running
    val zoneId: ZoneId,
    /** Local date the shift started on (denormalised for range queries; derived from startUtc+zoneId). */
    val workDate: LocalDate,
    val note: String? = null,
    val edited: Boolean = false,
)

@Entity(
    tableName = "breaks",
    foreignKeys = [ForeignKey(
        entity = ShiftEntity::class, parentColumns = ["id"], childColumns = ["shiftId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("shiftId")],
)
data class BreakEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shiftId: Long,
    val startUtc: Instant,
    val endUtc: Instant?,      // null = running
    val isAutoDeducted: Boolean = false,
)

@Entity(
    tableName = "day_marks",
    primaryKeys = ["date", "jobId"],
    foreignKeys = [ForeignKey(
        entity = JobEntity::class, parentColumns = ["id"], childColumns = ["jobId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("jobId")],
)
data class DayMarkEntity(
    val date: LocalDate,
    val jobId: Long,
    val type: DayMarkType,
    val paidMinutes: Int,
)

data class ShiftWithBreaks(
    @Embedded val shift: ShiftEntity,
    @Relation(parentColumn = "id", entityColumn = "shiftId") val breaks: List<BreakEntity>,
)

data class JobWithRules(
    @Embedded val job: JobEntity,
    @Relation(parentColumn = "ruleSetId", entityColumn = "id") val rules: RuleSetEntity,
)
