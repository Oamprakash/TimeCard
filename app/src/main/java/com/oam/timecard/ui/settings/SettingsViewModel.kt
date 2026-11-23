package com.oam.timecard.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oam.timecard.ads.ConsentManager
import com.oam.timecard.backup.BackupFormatException
import com.oam.timecard.billing.BillingManager
import com.oam.timecard.clock.ClockController
import com.oam.timecard.data.backup.BackupRepository
import com.oam.timecard.data.repository.TimeCardRepository
import com.oam.timecard.data.settings.AppSettings
import com.oam.timecard.data.settings.SettingsRepository
import com.oam.timecard.domain.engine.PayPeriodConfig
import com.oam.timecard.ui.JobLabel
import com.oam.timecard.ui.toLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsJob(val label: JobLabel, val archived: Boolean)

data class SettingsUiState(
    val settings: AppSettings? = null,
    val jobs: List<SettingsJob> = emptyList(),
    val price: String? = null,
    val purchasePending: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
) {
    val adsRemoved: Boolean get() = settings?.removeAds == true
}

sealed interface SettingsEvent {
    data class Share(val intent: Intent) : SettingsEvent
    data class Imported(val jobs: Int, val shifts: Int) : SettingsEvent
    data object ImportFailed : SettingsEvent
    data object ExportFailed : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    repo: TimeCardRepository,
    private val billing: BillingManager,
    private val consent: ConsentManager,
    private val backup: BackupRepository,
    private val controller: ClockController,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings, repo.allJobs, billing.price, billing.pending, consent.privacyOptionsRequired,
    ) { s, jobs, price, pending, privacy ->
        SettingsUiState(s, jobs.map { SettingsJob(it.toLabel(), it.job.archived) }, price, pending, privacy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { billing.refresh() }

    fun setPayPeriod(config: PayPeriodConfig, weekStart: DayOfWeek) = viewModelScope.launch {
        settings.setPayPeriod(config)
        settings.setWeekStart(weekStart)
    }

    /** Re-plans reminders for a running shift right away. */
    fun setReminders(hours: Int?, ruleWarnings: Boolean) = viewModelScope.launch {
        settings.setReminders(hours, ruleWarnings)
        controller.sync()
    }

    fun exportBackup() = viewModelScope.launch {
        try {
            _events.send(SettingsEvent.Share(backup.exportShareIntent()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.send(SettingsEvent.ExportFailed)
        }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        try {
            val r = backup.import(uri)
            _events.send(SettingsEvent.Imported(r.jobs, r.shifts))
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupFormatException) {
            _events.send(SettingsEvent.ImportFailed)
        } catch (e: Exception) {
            _events.send(SettingsEvent.ImportFailed)
        }
    }

    fun buyRemoveAds(activity: Activity): Boolean = billing.purchase(activity)
    fun restore() = billing.refresh()
    fun privacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)
}
