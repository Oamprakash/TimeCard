package com.oam.timecard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class RootState { LOADING, ONBOARDING, MAIN }

@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsRepository, repo: TimeCardRepository) : ViewModel() {
    /** Anyone who already has a job (e.g. from a test build before onboarding existed) skips onboarding. */
    val state: StateFlow<RootState> = combine(settings.settings, repo.allJobs) { s, jobs ->
        if (s.onboardingDone || jobs.isNotEmpty()) RootState.MAIN else RootState.ONBOARDING
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.LOADING)
}
