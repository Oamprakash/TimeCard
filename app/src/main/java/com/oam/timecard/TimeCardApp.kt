package com.oam.timecard

import android.app.Application
import com.oam.timecard.ads.AdsGate
import com.oam.timecard.ads.InterstitialManager
import com.oam.timecard.ads.RewardedManager
import com.oam.timecard.billing.BillingManager
import com.oam.timecard.clock.ClockController
import com.oam.timecard.clock.ClockNotifier
import com.oam.timecard.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class TimeCardApp : Application() {
    @Inject lateinit var notifier: ClockNotifier
    @Inject lateinit var clockController: ClockController
    @Inject lateinit var billing: BillingManager
    @Inject lateinit var adsGate: AdsGate
    @Inject lateinit var interstitial: InterstitialManager
    @Inject lateinit var rewarded: RewardedManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.createChannel()
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
