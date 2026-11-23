package com.oam.timecard.clock

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
import com.oam.timecard.MainActivity
import com.oam.timecard.R
import com.oam.timecard.domain.engine.RunningSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ongoing "clocked in" notification. No foreground service: the shift start is in Room and the
 * notification uses the system chronometer, so nothing needs to keep running. Actions never show ads.
 */
@Singleton
class ClockNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val CHANNEL_ID = "clock_status"
        const val NOTIFICATION_ID = 1001
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW, // silent, but visible in the shade and on the lock screen
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(snapshot: RunningSnapshot, jobName: String, now: Instant = Instant.now()) {
        if (!canPost()) return

        val title = if (snapshot.onBreak) context.getString(R.string.notif_on_break, jobName)
        else context.getString(R.string.notif_clocked_in, jobName)

        val breakAction = if (snapshot.onBreak)
            action(R.string.action_end_break, ClockActionReceiver.ACTION_END_BREAK, 2)
        else action(R.string.action_start_break, ClockActionReceiver.ACTION_START_BREAK, 2)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(title)
            .setContentText(if (snapshot.onBreak) context.getString(R.string.notif_break_text)
                            else context.getString(R.string.notif_working_text))
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(snapshot.chronometerBase(now))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp())
            .addAction(breakAction)
            .addAction(action(R.string.action_clock_out, ClockActionReceiver.ACTION_CLOCK_OUT, 1))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; the Clock tab still works.
        }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun action(label: Int, action: String, requestCode: Int): NotificationCompat.Action {
        val intent = Intent(context, ClockActionReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, context.getString(label), pi).build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
