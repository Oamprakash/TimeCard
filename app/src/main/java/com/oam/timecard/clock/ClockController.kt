package com.oam.timecard.clock

import com.oam.timecard.data.db.toDomain
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.domain.engine.RunningState
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place that changes clock state. The Clock tab, notification actions and the boot receiver
 * all go through here, so the notification always matches the database.
 */
@Singleton
class ClockController @Inject constructor(
    private val repo: TimeCardRepository,
    private val notifier: ClockNotifier,
    private val clock: Clock,
) {
    // Serialises taps from the UI and the notification arriving at the same moment.
    private val lock = Mutex()

    suspend fun clockIn(jobId: Long): Long = lock.withLock {
        val id = repo.clockIn(jobId)
        syncLocked()
        id
    }

    suspend fun clockOut() = lock.withLock {
        repo.clockOut()
        notifier.cancel()
    }

    suspend fun startBreak() = lock.withLock { repo.startBreak(); syncLocked() }

    suspend fun endBreak() = lock.withLock { repo.endBreak(); syncLocked() }

    /** Re-posts or removes the notification from what is stored. Safe to call any time (app start, boot, time change). */
    suspend fun sync() = lock.withLock { syncLocked() }

    private suspend fun syncLocked() {
        val running = repo.running()
        if (running == null) {
            notifier.cancel()
            return
        }
        val now = clock.instant()
        val jobName = repo.jobWithRules(running.shift.jobId)?.job?.name.orEmpty()
        notifier.show(RunningState.snapshot(running.toDomain(), now), jobName, now)
    }
}
