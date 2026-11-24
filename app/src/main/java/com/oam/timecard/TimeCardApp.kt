package com.oam.timecard

import android.app.Application
import com.oam.timecard.clock.ClockController
import com.oam.timecard.clock.ClockNotifier
import com.oam.timecard.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class TimeCardApp : Application() {
    @Inject lateinit var notifier: ClockNotifier
    @Inject lateinit var clockController: ClockController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.createChannel()
        // Puts the ongoing notification back if it was swiped away or the process was killed.
        appScope.launch { clockController.sync() }
    }
}
