package com.oamsapps.punchd

import android.app.Application
import com.oamsapps.punchd.ads.AdsGate
import com.oamsapps.punchd.ads.InterstitialManager
import com.oamsapps.punchd.ads.RewardedManager
import com.oamsapps.punchd.billing.BillingManager
import com.oamsapps.punchd.clock.ClockController
import com.oamsapps.punchd.clock.ClockNotifier
import com.oamsapps.punchd.clock.Reminders
import com.oamsapps.punchd.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class TimeCardApp : Application() {
    @Inject lateinit var notifier: ClockNotifier
    @Inject lateinit var clockController: ClockController
    @Inject lateinit var reminders: Reminders
    @Inject lateinit var billing: BillingManager
    @Inject lateinit var adsGate: AdsGate
    @Inject lateinit var interstitial: InterstitialManager
    @Inject lateinit var rewarded: RewardedManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.createChannel()
        reminders.createChannel()
        // Puts the ongoing notification back if it was swiped away or the process was killed.
        appScope.launch { clockController.sync() }
        // Restores "Remove ads" (and notices refunds) on every start.
        billing.refresh()
        // Full-screen ads are preloaded once consent allows ads; loading must happen on the main thread.
        appScope.launch(Dispatchers.Main) {
            adsGate.showAds.collect { show ->
                if (show) {
                    interstitial.preload()
                    rewarded.preload()
                }
            }
        }
    }
}
