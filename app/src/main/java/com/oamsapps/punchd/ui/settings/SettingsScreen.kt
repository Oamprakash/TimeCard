package com.oamsapps.punchd.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.punchd.BuildConfig
import com.oamsapps.punchd.R
import com.oamsapps.punchd.data.settings.DEFAULT_STILL_CLOCKED_IN_HOURS
import com.oamsapps.punchd.domain.engine.PayPeriodType
import com.oamsapps.punchd.ui.JobDot
import com.oamsapps.punchd.ui.PayPeriodDialog
import com.oamsapps.punchd.ui.findActivity
import com.oamsapps.punchd.ui.formatDateMedium
import com.oamsapps.punchd.ui.periodTypeLabel
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Hosted on the developer site (GitHub Pages). See docs/privacy-policy.md. */
const val PRIVACY_POLICY_URL = "https://oamprakash.github.io/punchd/privacy.html"

@Composable
fun SettingsScreen(
    onEditJob: (Long) -> Unit,
    onAddJob: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editPeriod by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<Uri?>(null) }

    val unavailable = stringResource(R.string.billing_unavailable)
    val importFailed = stringResource(R.string.backup_import_failed)
    val exportFailed = stringResource(R.string.export_failed)
    val importedFmt = stringResource(R.string.backup_imported)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SettingsEvent.Share -> runCatching { context.startActivity(e.intent) }
                is SettingsEvent.Imported -> snackbar.showSnackbar(String.format(importedFmt, e.jobs, e.shifts))
                SettingsEvent.ImportFailed -> snackbar.showSnackbar(importFailed)
                SettingsEvent.ExportFailed -> snackbar.showSnackbar(exportFailed)
            }
        }
    }

    val settings = s.settings
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Jobs & rules
            Section(stringResource(R.string.settings_jobs)) {
                s.jobs.forEach { j ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditJob(j.label.id) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JobDot(j.label.color, 12.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(j.label.name, modifier = Modifier.weight(1f))
                        if (j.archived) Text(stringResource(R.string.archived), style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onAddJob) { Text(stringResource(R.string.add_job)) }
            }

            // Pay period & week
            if (settings != null) {
                Section(stringResource(R.string.pay_period)) {
                    Text(periodTypeLabel(settings.payPeriod.type), fontWeight = FontWeight.SemiBold)
                    if (settings.payPeriod.type != PayPeriodType.SEMI_MONTHLY) {
                        Text(stringResource(R.string.period_starts_on, formatDateMedium(settings.payPeriod.anchor)))
                    }
                    Text(stringResource(R.string.week_starts_on) + ": " + settings.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    TextButton(onClick = { editPeriod = true }) { Text(stringResource(R.string.change)) }
                }

                // Reminders
                Section(stringResource(R.string.settings_reminders)) {
                    SwitchRow(
                        title = stringResource(R.string.reminder_still_title),
                        subtitle = stringResource(R.string.reminder_still_text),
                        checked = settings.stillClockedInHours != null,
                        onChange = { on -> viewModel.setReminders(if (on) DEFAULT_STILL_CLOCKED_IN_HOURS else null, settings.ruleWarnings) },
                    )
                    settings.stillClockedInHours?.let { current ->
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 8, 10, 12).forEach { h ->
                                FilterChip(
                                    selected = current == h,
                                    onClick = { viewModel.setReminders(h, settings.ruleWarnings) },
                                    label = { Text(stringResource(R.string.after_hours, h)) },
                                )
                            }
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.reminder_rules_title),
                        subtitle = stringResource(R.string.reminder_rules_text),
                        checked = settings.ruleWarnings,
                        onChange = { on -> viewModel.setReminders(settings.stillClockedInHours, on) },
                    )
                }
            }

            // Language (per-app language is an Android 13+ system feature)
            Section(stringResource(R.string.settings_language)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        }
                    }) { Text(stringResource(R.string.app_language)) }
                } else {
                    Text(stringResource(R.string.language_follows_device), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Backup
            Section(stringResource(R.string.settings_backup)) {
                Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.exportBackup() }) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }

            // Remove ads
            Section(stringResource(R.string.remove_ads)) {
                Text(stringResource(R.string.remove_ads_benefits), style = MaterialTheme.typography.bodyMedium)
                when {
                    s.adsRemoved -> Text(stringResource(R.string.remove_ads_owned), color = MaterialTheme.colorScheme.primary)
                    s.purchasePending -> Text(stringResource(R.string.purchase_pending))
                    else -> Button(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null || !viewModel.buyRemoveAds(activity)) scope.launch { snackbar.showSnackbar(unavailable) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(s.price?.let { stringResource(R.string.remove_ads_for, it) } ?: stringResource(R.string.remove_ads)) }
                }
                TextButton(onClick = { viewModel.restore() }) { Text(stringResource(R.string.restore_purchases)) }
            }

            // Privacy & about
            Section(stringResource(R.string.settings_about)) {
                if (s.privacyOptionsRequired) {
                    TextButton(onClick = { context.findActivity()?.let(viewModel::privacyOptions) }) {
                        Text(stringResource(R.string.privacy_options))
                    }
                }
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) } }) {
                    Text(stringResource(R.string.privacy_policy))
                }
                Text(stringResource(R.string.pay_disclaimer), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (editPeriod && settings != null) {
        PayPeriodDialog(
            initial = settings.payPeriod,
            initialWeekStart = settings.weekStart,
            onDismiss = { editPeriod = false },
            onSave = { c, ws -> viewModel.setPayPeriod(c, ws); editPeriod = false },
        )
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text(stringResource(R.string.backup_import_q)) },
            text = { Text(stringResource(R.string.backup_import_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmImport = null; viewModel.importBackup(uri) }) { Text(stringResource(R.string.backup_import)) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
