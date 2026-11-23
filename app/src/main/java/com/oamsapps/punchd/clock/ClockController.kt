package com.oamsapps.punchd.clock

import android.content.Context
import com.oamsapps.punchd.data.db.toDomain
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.domain.engine.RunningState
import com.oamsapps.punchd.widget.TimeCardWidget
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repo: TimeCardRepository,
    private val notifier: ClockNotifier,
    private val reminders: Reminders,
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
        syncLocked()
    }

    suspend fun startBreak() = lock.withLock { repo.startBreak(); syncLocked() }

    suspend fun endBreak() = lock.withLock { repo.endBreak(); syncLocked() }

    /**
     * Brings the notification, reminders and home-screen widget in line with what is stored.
     * Safe to call any time (app start, boot, time change, after edits or a backup import).
     */
    suspend fun sync() = lock.withLock { syncLocked() }

    private suspend fun syncLocked() {
        val running = repo.running()
        if (running == null) {
            notifier.cancel()
            reminders.stop()
        } else {
            val now = clock.instant()
            val jobName = repo.jobWithRules(running.shift.jobId)?.job?.name.orEmpty()
            notifier.show(RunningState.snapshot(running.toDomain(), now), jobName, now)
            reminders.check()
        }
        runCatching { TimeCardWidget.refresh(context) }
    }
}
