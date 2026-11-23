package com.oamsapps.punchd.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [RuleSetEntity::class, JobEntity::class, ShiftEntity::class, BreakEntity::class, DayMarkEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TimeCardDatabase : RoomDatabase() {
    abstract fun ruleSetDao(): RuleSetDao
    abstract fun jobDao(): JobDao
    abstract fun shiftDao(): ShiftDao
    abstract fun dayMarkDao(): DayMarkDao

    companion object { const val NAME = "punchd.db" }
}
