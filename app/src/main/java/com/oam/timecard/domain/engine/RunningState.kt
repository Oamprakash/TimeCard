package com.oam.timecard.domain.engine

import com.oam.timecard.domain.model.WorkShift
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** What the Clock tab and the ongoing notification need to show for a running shift. */
data class RunningSnapshot(
    val shiftId: Long,
    val jobId: Long,
    val start: Instant,
    /** Worked time so far (breaks excluded). */
    val worked: Duration,
    val onBreak: Boolean,
    val breakStart: Instant?,
    /** All break time so far, including a running break. */
    val breakTotal: Duration,
) {
    /**
     * Base for a counting-up Chronometer, in epoch millis.
     * Working: the chronometer shows worked time (base = now - worked), so breaks are excluded.
     * On break: it shows the length of the current break.
     */
    fun chronometerBase(now: Instant): Long =
        if (onBreak && breakStart != null) breakStart.toEpochMilli()
        else now.minus(worked).toEpochMilli()
}

object RunningState {
    fun snapshot(shift: WorkShift, now: Instant): RunningSnapshot {
        val end = shift.end ?: now
        val breaks = HoursCalculator.breakTotal(shift.breaks, shift.start, end, now)
        val gross = Duration.between(shift.start, end).let { if (it.isNegative) Duration.ZERO else it }
        val worked = gross.minus(breaks).let { if (it.isNegative) Duration.ZERO else it }
        val openBreak = shift.breaks.lastOrNull { it.end == null }
        return RunningSnapshot(
            shiftId = shift.id,
            jobId = shift.jobId,
            start = shift.start,
            worked = worked,
            onBreak = openBreak != null,
            breakStart = openBreak?.start,
            breakTotal = breaks,
        )
    }
}

/** Splits a duration for display. Formatting strings live in resources (EN/DE). */
object DurationParts {
    data class Hms(val hours: Long, val minutes: Int, val seconds: Int)

    fun of(d: Duration): Hms {
        val s = if (d.isNegative) 0 else d.seconds
        return Hms(s / 3600, ((s % 3600) / 60).toInt(), (s % 60).toInt())
    }

    /** "3:07:09" — used for the live timer. */
    fun clock(d: Duration): String = of(d).let { String.format(Locale.ROOT, "%d:%02d:%02d", it.hours, it.minutes, it.seconds) }

    /** Fraction of [part] over [whole], clamped to 0..1; 0 when there is no threshold. */
    fun progress(part: Duration, whole: Duration?): Float {
        if (whole == null || whole.isZero || whole.isNegative) return 0f
        return (part.seconds.toDouble() / whole.seconds).coerceIn(0.0, 1.0).toFloat()
    }
}
