package com.oamsapps.punchd.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oamsapps.punchd.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ClockEntryPoint {
    fun clockController(): ClockController
    @ApplicationScope fun scope(): CoroutineScope
}

/** Runs [block] off the main thread while keeping the receiver alive (goAsync). */
private fun BroadcastReceiver.runAsync(context: Context, block: suspend (ClockController) -> Unit) {
    val entry = EntryPointAccessors.fromApplication(context.applicationContext, ClockEntryPoint::class.java)
    val pending = goAsync()
    entry.scope().launch {
        try {
            block(entry.clockController())
        } finally {
            pending.finish()
        }
    }
}

/** Buttons on the ongoing notification. Never shows an ad. */
class ClockActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CLOCK_OUT = "com.oamsapps.punchd.action.CLOCK_OUT"
        const val ACTION_START_BREAK = "com.oamsapps.punchd.action.START_BREAK"
        const val ACTION_END_BREAK = "com.oamsapps.punchd.action.END_BREAK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        runAsync(context) { controller ->
            when (action) {
                ACTION_CLOCK_OUT -> controller.clockOut()
                ACTION_START_BREAK -> controller.startBreak()
                ACTION_END_BREAK -> controller.endBreak()
            }
        }
    }
}

/**
 * The running shift lives in Room, so it already survives reboot and app kill; this only puts the
 * notification back. Time and zone changes re-post it so the chronometer base stays right.
 */
class ClockRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> runAsync(context) { it.sync() }
        }
    }
}
