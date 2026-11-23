package com.oam.timecard.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oam.timecard.ads.ConsentManager
import com.oam.timecard.billing.BillingManager
import com.oam.timecard.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val adsRemoved: Boolean = false,
    val price: String? = null,
    val purchasePending: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
)

/** Sprint 4 subset of Settings: Remove ads, restore, privacy options. The rest arrives in Sprint 5. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    settings: SettingsRepository,
    private val billing: BillingManager,
    private val consent: ConsentManager,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings, billing.price, billing.pending, consent.privacyOptionsRequired,
    ) { s, price, pending, privacy ->
        SettingsUiState(s.removeAds, price, pending, privacy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init { billing.refresh() }

    fun buyRemoveAds(activity: Activity): Boolean = billing.purchase(activity)
    fun restore() = billing.refresh()
    fun privacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)
}
