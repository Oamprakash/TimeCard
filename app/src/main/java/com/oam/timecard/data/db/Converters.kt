package com.oam.timecard.data.db

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Converters {
    @TypeConverter fun instantToLong(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)

    @TypeConverter fun zoneToString(v: ZoneId?): String? = v?.id
    @TypeConverter fun stringToZone(v: String?): ZoneId? = v?.let(ZoneId::of)

    /** Stored as epoch day so range queries and ordering work on an INTEGER column. */
    @TypeConverter fun dateToLong(v: LocalDate?): Long? = v?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    /** Stored as TEXT to keep rates exact (no floating point). */
    @TypeConverter fun decimalToString(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun stringToDecimal(v: String?): BigDecimal? = v?.let(::BigDecimal)
}
