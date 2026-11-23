package com.oamsapps.punchd.domain.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A break as typed in the editor: wall-clock times on the shift's day (or the next day for night shifts). */
data class BreakDraft(val start: LocalTime, val end: LocalTime)

/**
 * A shift as typed in the editor. [end] == null only for the running shift, whose end is not editable.
 * Times later than midnight are entered as they read on the clock; [resolve] rolls them to the next day.
 */
data class ShiftDraft(
    val id: Long? = null,
    val jobId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime?,
    val breaks: List<BreakDraft> = emptyList(),
    val note: String? = null,
)

data class ResolvedBreak(val start: Instant, val end: Instant)
data class ResolvedShift(val start: Instant, val end: Instant?, val zone: ZoneId, val breaks: List<ResolvedBreak>)

enum class ShiftError {
    /** Start and end are the same time. */
    EMPTY,
    START_IN_FUTURE,
    END_IN_FUTURE,
    BREAK_EMPTY,
    BREAK_OUTSIDE_SHIFT,
    BREAKS_OVERLAP,
    BREAKS_LONGER_THAN_SHIFT,
    OVERLAPS_OTHER_SHIFT,
}

object ShiftDrafts {

    /**
     * Turns wall-clock times into instants in [zone].
     * - End at or before start (on the clock) means the next day: 22:00–06:00 is an 8 h night shift.
     * - A break time earlier than the shift start is on the next day.
     * - A time inside a DST gap (e.g. 02:30 on spring-forward night) moves forward by the gap, as java.time does.
     */
    fun resolve(draft: ShiftDraft, zone: ZoneId): ResolvedShift {
        fun at(t: LocalTime): Instant {
            val day = if (t < draft.start) draft.date.plusDays(1) else draft.date
            return day.atTime(t).atZone(zone).toInstant()
        }
        val start = draft.date.atTime(draft.start).atZone(zone).toInstant()
        val end = draft.end?.let { e ->
            if (e == draft.start) start // EMPTY, reported by validate()
            else at(e)
        }
        // A break crossing midnight (23:50–00:20 in a 22:00 shift) resolves correctly through at().
        // A break typed backwards in a day shift (12:30–12:00) stays backwards and fails validation.
        val breaks = draft.breaks.map { b -> ResolvedBreak(at(b.start), at(b.end)) }
        return ResolvedShift(start, end, zone, breaks)
    }

    /**
     * Checks a resolved shift. [now] guards against future punches; [overlappingShifts] is how many
     * other stored shifts intersect this one (from the DAO).
     */
    fun validate(shift: ResolvedShift, now: Instant, overlappingShifts: Int): Set<ShiftError> = buildSet {
        val end = shift.end
        if (shift.start.isAfter(now)) add(ShiftError.START_IN_FUTURE)
        if (end != null) {
            if (!end.isAfter(shift.start)) add(ShiftError.EMPTY)
            if (end.isAfter(now)) add(ShiftError.END_IN_FUTURE)
        }
        val shiftEnd = end ?: now
        val sorted = shift.breaks.sortedBy { it.start }
        sorted.forEach { b ->
            if (!b.end.isAfter(b.start)) add(ShiftError.BREAK_EMPTY)
            if (b.start.isBefore(shift.start) || b.end.isAfter(shiftEnd)) add(ShiftError.BREAK_OUTSIDE_SHIFT)
        }
        sorted.zipWithNext().forEach { (a, b) -> if (b.start.isBefore(a.end)) add(ShiftError.BREAKS_OVERLAP) }
        val breakTotal = sorted.fold(Duration.ZERO) { acc, b -> acc.plus(Duration.between(b.start, b.end)) }
        if (end != null && end.isAfter(shift.start) && breakTotal >= Duration.between(shift.start, end)) {
            add(ShiftError.BREAKS_LONGER_THAN_SHIFT)
        }
        if (overlappingShifts > 0) add(ShiftError.OVERLAPS_OTHER_SHIFT)
    }
}
