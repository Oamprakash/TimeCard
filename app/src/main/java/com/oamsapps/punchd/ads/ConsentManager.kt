package com.oamsapps.punchd.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.oamsapps.punchd.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google UMP consent (GDPR in the EU/UK, US state privacy laws). Ads are only requested once
 * [canRequestAds] is true, and the Mobile Ads SDK is only initialised then.
 * With non-personalised consent the SDK serves non-personalised ads automatically via the TCF string.
 */
@Singleton
class ConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val adsInitialised = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** True when the user must be offered a "Privacy options" entry (EU/UK, some US states). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Call from the launcher activity's onCreate. Shows the consent form only when it is required. */
    fun gather(activity: Activity) {
        info.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { refresh() } },
            { refresh() }, // offline or error: fall back to consent from an earlier session, if any
        )
        // Consent given in an earlier session lets ads start without waiting for the network.
        refresh()
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refresh() }
    }

    private fun refresh() {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        val can = info.canRequestAds()
        _canRequestAds.value = can
        if (can && adsInitialised.compareAndSet(false, true)) {
            scope.launch(Dispatchers.IO) { MobileAds.initialize(context) }
        }
    }
}
