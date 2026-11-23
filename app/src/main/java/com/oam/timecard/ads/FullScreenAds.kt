package com.oam.timecard.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.oam.timecard.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Interstitial after saving a manual edit, capped by [InterstitialPolicy].
 * Load and show must run on the main thread.
 */
@Singleton
class InterstitialManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: AdsGate,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private var ad: InterstitialAd? = null
    private var loading = false

    fun preload() {
        if (!gate.showAds.value || ad != null || loading) return
        loading = true
        InterstitialAd.load(context, AdUnits.interstitial, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(loaded: InterstitialAd) { ad = loaded; loading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { loading = false }
        })
    }

    /** Shows an interstitial if allowed and ready; returns when it is closed (or at once if none is shown). */
    suspend fun maybeShow(activity: Activity) {
        if (!gate.showAds.value) return
        val now = clock.instant()
        val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
        val history = settings.interstitialHistory()
        if (!InterstitialPolicy.canShow(history, now, today)) return
        val loaded = ad ?: run { preload(); return }
        ad = null
        settings.saveInterstitialHistory(InterstitialPolicy.recordShown(history, now, today))
        suspendCancellableCoroutine { cont ->
            loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { if (cont.isActive) cont.resume(Unit) }
                override fun onAdFailedToShowFullScreenContent(error: AdError) { if (cont.isActive) cont.resume(Unit) }
            }
            loaded.show(activity)
        }
        preload()
    }
}

enum class RewardResult { EARNED, NOT_EARNED, UNAVAILABLE }

/** Rewarded ad that unlocks one PDF or Excel export. The user always opts in first (see Reports). */
@Singleton
class RewardedManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gate: AdsGate,
) {
    private var ad: RewardedAd? = null
    private var loading = false

    fun preload() {
        if (!gate.showAds.value || ad != null || loading) return
        loading = true
        RewardedAd.load(context, AdUnits.rewarded, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(loaded: RewardedAd) { ad = loaded; loading = false }
            override fun onAdFailedToLoad(error: LoadAdError) { loading = false }
        })
    }

    val isReady: Boolean get() = ad != null

    /**
     * UNAVAILABLE when no ad could be loaded (offline, no fill). Callers export anyway in that case:
     * an offline-first app shouldn't lock the user's own data behind a network request.
     */
    suspend fun show(activity: Activity): RewardResult {
        val loaded = ad ?: run { preload(); return RewardResult.UNAVAILABLE }
        ad = null
        var earned = false
        val result = suspendCancellableCoroutine { cont ->
            loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (cont.isActive) cont.resume(if (earned) RewardResult.EARNED else RewardResult.NOT_EARNED)
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (cont.isActive) cont.resume(RewardResult.UNAVAILABLE)
                }
            }
            loaded.show(activity) { earned = true }
        }
        preload()
        return result
    }
}
