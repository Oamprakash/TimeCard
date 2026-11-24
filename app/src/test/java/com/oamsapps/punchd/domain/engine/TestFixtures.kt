package com.oamsapps.punchd.domain.engine

import com.oamsapps.punchd.domain.model.BreakSpan
import com.oamsapps.punchd.domain.model.WorkShift
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
val LOS_ANGELES: ZoneId = ZoneId.of("America/Los_Angeles")
val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

fun d(iso: String): LocalDate = LocalDate.parse(iso)
fun at(isoLocal: String, zone: ZoneId): Instant = LocalDateTime.parse(isoLocal).atZone(zone).toInstant()
fun hours(h: Double): Duration = Duration.ofSeconds(Math.round(h * 3600))
fun mins(m: Long): Duration = Duration.ofMinutes(m)

private var nextId = 1L

/** Shift starting at local [date]T[time] lasting [lengthH] wall-clock-independent hours. */
fun shift(
    date: String,
    time: String,
    lengthH: Double,
    zone: ZoneId,
    jobId: Long = 1,
    breaks: List<BreakSpan> = emptyList(),
): WorkShift {
    val start = at("${date}T$time", zone)
    return WorkShift(nextId++, jobId, start, start.plus(hours(lengthH)), zone, breaks)
}

/** Shift between two local date-times, e.g. across a DST change. */
fun shiftLocal(startLocal: String, endLocal: String, zone: ZoneId, jobId: Long = 1, breaks: List<BreakSpan> = emptyList()) =
    WorkShift(nextId++, jobId, at(startLocal, zone), at(endLocal, zone), zone, breaks)

fun brk(startLocal: String, endLocal: String?, zone: ZoneId) =
    BreakSpan(at(startLocal, zone), endLocal?.let { at(it, zone) })
