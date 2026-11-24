package com.oamsapps.punchd.ads

import com.oamsapps.punchd.BuildConfig
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

object AdUnits {
    val banner: String get() = BuildConfig.ADMOB_BANNER_ID
    val native: String get() = BuildConfig.ADMOB_NATIVE_ID
    val rewarded: String get() = BuildConfig.ADMOB_REWARDED_ID
    val interstitial: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID
}

/** Ads show only when consent allows it and the user hasn't bought "Remove ads". */
@Singleton
class AdsGate @Inject constructor(
    consent: ConsentManager,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val showAds: StateFlow<Boolean> = combine(consent.canRequestAds, settings.settings) { can, s -> can && !s.removeAds }
        .stateIn(scope, SharingStarted.Eagerly, false)
}

/** For composables (banner, native row) that can't use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdsEntryPoint {
    fun adsGate(): AdsGate
}
