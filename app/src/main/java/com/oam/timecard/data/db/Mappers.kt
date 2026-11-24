package com.oam.timecard.data.db

import com.oam.timecard.domain.model.BreakRequirement
import com.oam.timecard.domain.model.BreakSpan
import com.oam.timecard.domain.model.DayMarkEntry
import com.oam.timecard.domain.model.PresetKind
import com.oam.timecard.domain.model.RulePresets
import com.oam.timecard.domain.model.RuleSet
import com.oam.timecard.domain.model.WorkShift
import java.time.Duration

private fun Int?.minutes(): Duration? = this?.let { Duration.ofMinutes(it.toLong()) }
private fun Duration?.toMin(): Int? = this?.toMinutes()?.toInt()

fun RuleSetEntity.toDomain(): RuleSet = RuleSet(
    dailyOtAfter = dailyOtAfterMin.minutes(),
    dailyDoubleAfter = dailyDoubleAfterMin.minutes(),
    weeklyOtAfter = weeklyOtAfterMin.minutes(),
    otMultiplier = otMultiplier,
    doubleMultiplier = doubleMultiplier,
    autoBreak = Duration.ofMinutes(autoBreakMin.toLong()),
    autoBreakAfter = autoBreakAfterMin.minutes(),
    maxDaily = maxDailyMin.minutes(),
    breakRequirements = listOfNotNull(
        breakReq(breakWarn1AfterMin, breakWarn1Min),
        breakReq(breakWarn2AfterMin, breakWarn2Min),
    ),
)

private fun breakReq(after: Int?, min: Int?) =
    if (after != null && min != null) BreakRequirement(after.minutes()!!, min.minutes()!!) else null

/** Copies a built-in preset into a new, job-owned rules row. */
fun PresetKind.toNewRuleSetEntity(): RuleSetEntity {
    val r = RulePresets.forKind(this)
    val w1 = r.breakRequirements.getOrNull(0)
    val w2 = r.breakRequirements.getOrNull(1)
    return RuleSetEntity(
        presetKind = this,
        dailyOtAfterMin = r.dailyOtAfter.toMin(),
        dailyDoubleAfterMin = r.dailyDoubleAfter.toMin(),
        weeklyOtAfterMin = r.weeklyOtAfter.toMin(),
        otMultiplier = r.otMultiplier,
        doubleMultiplier = r.doubleMultiplier,
        autoBreakMin = r.autoBreak.toMinutes().toInt(),
        autoBreakAfterMin = r.autoBreakAfter.toMin(),
        maxDailyMin = r.maxDaily.toMin(),
        breakWarn1AfterMin = w1?.workedOver.toMin(),
        breakWarn1Min = w1?.minBreak.toMin(),
        breakWarn2AfterMin = w2?.workedOver.toMin(),
        breakWarn2Min = w2?.minBreak.toMin(),
    )
}

fun ShiftWithBreaks.toDomain(): WorkShift = WorkShift(
    id = shift.id,
    jobId = shift.jobId,
    start = shift.startUtc,
    end = shift.endUtc,
    zone = shift.zoneId,
    breaks = breaks.map { BreakSpan(it.startUtc, it.endUtc) },
    note = shift.note,
)

fun DayMarkEntity.toDomain() = DayMarkEntry(date, jobId, type, Duration.ofMinutes(paidMinutes.toLong()))

fun JobWithRules.toJobInfo() = com.oam.timecard.domain.engine.JobInfo(
    id = job.id,
    rules = rules.toDomain(),
    hourlyRate = job.hourlyRate,
    scheduledPerWeek = job.scheduledMinPerWeek?.let { Duration.ofMinutes(it.toLong()) },
)
