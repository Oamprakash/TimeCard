package com.oamsapps.punchd.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oamsapps.punchd.ads.ConsentManager
import com.oamsapps.punchd.backup.BackupFormatException
import com.oamsapps.punchd.billing.BillingManager
import com.oamsapps.punchd.clock.ClockController
import com.oamsapps.punchd.data.backup.BackupRepository
import com.oamsapps.punchd.data.repository.TimeCardRepository
import com.oamsapps.punchd.data.settings.AppSettings
import com.oamsapps.punchd.data.settings.SettingsRepository
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.toLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import com.oamsapps.punchd.util.retryable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsJob(val label: JobLabel, val archived: Boolean)

data class SettingsUiState(
    val loadError: Boolean = false,
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
    /** [retry] is null when retrying can't help (the file isn't a valid backup). */
    data class ImportFailed(val retry: (() -> Unit)?) : SettingsEvent
    data object ExportFailed : SettingsEvent
    data object SaveFailed : SettingsEvent
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

    private val reloads = MutableStateFlow(0)
    fun reload() { reloads.value++ }

    val state: StateFlow<SettingsUiState> = retryable(reloads, { SettingsUiState(loadError = true) }) {
        combine(
            settings.settings, repo.allJobs, billing.price, billing.pending, consent.privacyOptionsRequired,
        ) { s, jobs, price, pending, privacy ->
            SettingsUiState(false, s, jobs.map { SettingsJob(it.toLabel(), it.job.archived) }, price, pending, privacy)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { billing.refresh() }

    fun setPayPeriod(config: PayPeriodConfig, weekStart: DayOfWeek) = save {
        settings.setPayPeriod(config)
        settings.setWeekStart(weekStart)
    }

    /** Re-plans reminders for a running shift right away. */
    fun setReminders(hours: Int?, ruleWarnings: Boolean) = save {
        settings.setReminders(hours, ruleWarnings)
        controller.sync()
    }

    private fun save(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _events.send(SettingsEvent.SaveFailed)
        }
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
            _events.send(SettingsEvent.ImportFailed(retry = null)) // a bad file stays bad
        } catch (e: Exception) {
            _events.send(SettingsEvent.ImportFailed(retry = { importBackup(uri) })) // e.g. couldn't read the file
        }
    }

    fun buyRemoveAds(activity: Activity): Boolean = billing.purchase(activity)
    fun restore() = billing.refresh()
    fun privacyOptions(activity: Activity) = consent.showPrivacyOptions(activity)
}
