package com.oamsapps.punchd.clock

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oamsapps.punchd.MainActivity
import com.oamsapps.punchd.R
import com.oamsapps.punchd.data.db.toDomain
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.AlertType
import com.oamsapps.punchd.domain.engine.HoursCalculator
import com.oamsapps.punchd.domain.engine.ReminderPlanner
import com.oamsapps.punchd.domain.engine.RunningState
import com.oamsapps.punchd.domain.model.RuleSet
import com.oamsapps.punchd.ui.formatHmText
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * "Still clocked in?" and the German break / 10 h warnings. [ReminderPlanner] says what is due and when
 * to look again; a single one-time WorkManager job sleeps until then. No polling, no foreground service.
 */
@Singleton
class Reminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: TimeCardRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    companion object {
        const val CHANNEL_ID = "reminders"
        private const val WORK_NAME = "clock_reminders"
        private const val NOTIFICATION_BASE_ID = 2000
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminders_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posts what is due now and schedules the next check. Called on every clock state change and by the worker. */
    suspend fun check() {
        val running = repo.running() ?: return stop()
        val job = repo.jobWithRules(running.shift.jobId) ?: return stop()
        val s = settings.settings.first()
        val now = clock.instant()
        val shift = running.toDomain()
        val snapshot = RunningState.snapshot(shift, now)
        val rules = job.rules.toDomain()

        // The 10 h limit is per day across shifts: add today's finished shifts.
        val today = now.atZone(shift.zone).toLocalDate()
        val earlier = repo.observeShifts(today, today).first()
            .filter { it.shift.id != shift.id && it.shift.endUtc != null }
            .fold(Duration.ZERO) { acc, other ->
                val d = other.toDomain()
                val end = d.end!!
                acc.plus(Duration.between(d.start, end).minus(HoursCalculator.breakTotal(d.breaks, d.start, end, end)))
            }

        val sent = settings.sentAlerts(shift.id)
        val plan = ReminderPlanner.plan(snapshot, rules, earlier, s.reminders, sent, now)
        plan.due.forEach { post(it, job.job.name, rules, snapshot.start) }
        if (plan.due.isNotEmpty()) settings.addSentAlerts(shift.id, plan.due)
        schedule(plan.nextCheck, now)
    }

    /** Clocked out: cancel the pending check and clear alerts. */
    suspend fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        settings.clearSentAlerts()
        AlertType.entries.forEach { NotificationManagerCompat.from(context).cancel(NOTIFICATION_BASE_ID + it.ordinal) }
    }

    private fun schedule(at: Instant?, now: Instant) {
        val wm = WorkManager.getInstance(context)
        if (at == null) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = Duration.between(now, at).toMillis().coerceAtLeast(0)
        wm.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
        )
    }

    private fun post(type: AlertType, jobName: String, rules: RuleSet, start: Instant) {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val reqs = rules.breakRequirements.sortedBy { it.workedOver }
        val (title, text) = when (type) {
            AlertType.STILL_CLOCKED_IN -> context.getString(R.string.alert_still_title) to
                context.getString(R.string.alert_still_text, jobName, formatHmText(context, Duration.between(start, clock.instant())))
            AlertType.BREAK_SHORT, AlertType.BREAK_LONG -> {
                val r = reqs[if (type == AlertType.BREAK_SHORT) 0 else 1]
                context.getString(R.string.alert_break_title) to
                    context.getString(R.string.alert_break_text, formatHmText(context, r.workedOver), formatHmText(context, r.minBreak))
            }
            AlertType.MAX_DAILY -> context.getString(R.string.alert_max_title) to
                context.getString(R.string.alert_max_text, formatHmText(context, rules.maxDaily ?: Duration.ZERO))
        }

        val open = PendingIntent.getActivity(
            context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (type == AlertType.STILL_CLOCKED_IN) {
            val clockOut = PendingIntent.getBroadcast(
                context, 11, Intent(context, ClockActionReceiver::class.java).setAction(ClockActionReceiver.ACTION_CLOCK_OUT),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, context.getString(R.string.action_clock_out), clockOut)
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE_ID + type.ordinal, builder.build())
        } catch (_: SecurityException) {
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RemindersEntryPoint {
    fun reminders(): Reminders
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, RemindersEntryPoint::class.java).reminders().check()
        return Result.success()
    }
}
