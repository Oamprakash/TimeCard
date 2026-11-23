package com.oam.timecard.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleSetDao {
    @Insert suspend fun insert(rules: RuleSetEntity): Long
    @Update suspend fun update(rules: RuleSetEntity)
    @Query("SELECT * FROM rule_sets WHERE id = :id") suspend fun get(id: Long): RuleSetEntity?
}

@Dao
interface JobDao {
    @Insert suspend fun insert(job: JobEntity): Long
    @Update suspend fun update(job: JobEntity)

    @Transaction
    @Query("SELECT * FROM jobs WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<JobWithRules>>

    @Transaction
    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getWithRules(id: Long): JobWithRules?

    @Query("SELECT COUNT(*) FROM jobs WHERE archived = 0")
    suspend fun activeCount(): Int
}

@Dao
interface ShiftDao {
    @Insert suspend fun insert(shift: ShiftEntity): Long
    @Update suspend fun update(shift: ShiftEntity)
    @Delete suspend fun delete(shift: ShiftEntity)

    @Insert suspend fun insertBreak(b: BreakEntity): Long
    @Update suspend fun updateBreak(b: BreakEntity)
    @Delete suspend fun deleteBreak(b: BreakEntity)

    /** At most one shift is running; the repository enforces this inside a transaction. */
    @Transaction
    @Query("SELECT * FROM shifts WHERE endUtc IS NULL LIMIT 1")
    fun observeRunning(): Flow<ShiftWithBreaks?>

    @Transaction
    @Query("SELECT * FROM shifts WHERE endUtc IS NULL LIMIT 1")
    suspend fun getRunning(): ShiftWithBreaks?

    @Query("SELECT * FROM breaks WHERE shiftId = :shiftId AND endUtc IS NULL LIMIT 1")
    suspend fun getOpenBreak(shiftId: Long): BreakEntity?

    @Query("UPDATE breaks SET endUtc = :end WHERE shiftId = :shiftId AND endUtc IS NULL")
    suspend fun closeOpenBreaks(shiftId: Long, end: Instant)

    @Transaction
    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun getById(id: Long): ShiftWithBreaks?

    @Query("DELETE FROM breaks WHERE shiftId = :shiftId")
    suspend fun deleteBreaksOf(shiftId: Long)

    @Query("DELETE FROM shifts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    @Query("SELECT * FROM shifts WHERE workDate BETWEEN :from AND :to ORDER BY startUtc")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ShiftWithBreaks>>

    @Transaction
    @Query("SELECT * FROM shifts WHERE jobId = :jobId AND workDate BETWEEN :from AND :to ORDER BY startUtc")
    suspend fun getRangeForJob(jobId: Long, from: LocalDate, to: LocalDate): List<ShiftWithBreaks>

    /** Overlap check for manual entries (end null = running, treated as open-ended). */
    @Query(
        """SELECT COUNT(*) FROM shifts
           WHERE id != :excludeId AND startUtc < :end AND (endUtc IS NULL OR endUtc > :start)"""
    )
    suspend fun countOverlapping(start: Instant, end: Instant, excludeId: Long): Int
}

@Dao
interface DayMarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(mark: DayMarkEntity)
    @Delete suspend fun delete(mark: DayMarkEntity)

    @Query("DELETE FROM day_marks WHERE date = :date AND jobId = :jobId")
    suspend fun deleteFor(date: LocalDate, jobId: Long)

    @Query("SELECT * FROM day_marks WHERE date BETWEEN :from AND :to")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DayMarkEntity>>

    @Query("SELECT * FROM day_marks WHERE jobId = :jobId AND date BETWEEN :from AND :to")
    suspend fun getRangeForJob(jobId: Long, from: LocalDate, to: LocalDate): List<DayMarkEntity>
}
