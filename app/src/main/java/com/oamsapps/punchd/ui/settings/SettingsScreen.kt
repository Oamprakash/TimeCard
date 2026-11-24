package com.oamsapps.punchd.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.oamsapps.punchd.ui.ErrorState
import com.oamsapps.punchd.ui.FormSkeleton
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    val importReadFailed = stringResource(R.string.backup_read_failed)
    val saveFailed = stringResource(R.string.save_failed)
    val retryLabel = stringResource(R.string.retry)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is SettingsEvent.Share -> runCatching { context.startActivity(e.intent) }
                is SettingsEvent.Imported -> snackbar.showSnackbar(String.format(importedFmt, e.jobs, e.shifts))
                is SettingsEvent.ImportFailed -> {
                    val retry = e.retry
                    if (retry == null) snackbar.showSnackbar(importFailed)
                    else if (snackbar.showSnackbar(importReadFailed, actionLabel = retryLabel, withDismissAction = true) == SnackbarResult.ActionPerformed) retry()
                }
                SettingsEvent.ExportFailed -> if (
                    snackbar.showSnackbar(exportFailed, actionLabel = retryLabel, withDismissAction = true) == SnackbarResult.ActionPerformed
                ) viewModel.exportBackup()
                SettingsEvent.SaveFailed -> snackbar.showSnackbar(saveFailed)
            }
        }
    }

    val settings = s.settings
    if (s.loadError) {
        ErrorState(onRetry = viewModel::reload)
        return
    }
    if (settings == null) {
        FormSkeleton(fields = 6)
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Jobs & rules: tappable rows (48dp, chevron) + "+ Add job"
            Section(stringResource(R.string.settings_jobs)) {
                s.jobs.forEach { j ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = stringResource(R.string.edit_job)) { onEditJob(j.label.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JobDot(j.label.color, 12.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(j.label.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (j.archived) {
                            Text(stringResource(R.string.archived), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAddJob) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_job))
                }
            }

            // Pay period: "Change" as the card's header action
            Section(
                title = stringResource(R.string.pay_period),
                action = {
                    TextButton(onClick = { editPeriod = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.change))
                    }
                },
            ) {
                Text(periodTypeLabel(settings.payPeriod.type), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                if (settings.payPeriod.type != PayPeriodType.SEMI_MONTHLY) {
                    Text(stringResource(R.string.period_starts_on, formatDateMedium(settings.payPeriod.anchor)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.week_starts_on) + ": " + settings.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Reminders: compact segmented hours, never clipped
            Section(stringResource(R.string.settings_reminders)) {
                SwitchRow(
                    title = stringResource(R.string.reminder_still_title),
                    subtitle = stringResource(R.string.reminder_still_text),
                    checked = settings.stillClockedInHours != null,
                    onChange = { on -> viewModel.setReminders(if (on) DEFAULT_STILL_CLOCKED_IN_HOURS else null, settings.ruleWarnings) },
                )
                settings.stillClockedInHours?.let { current ->
                    Text(stringResource(R.string.remind_after), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val options = listOf(6, 8, 10, 12)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, h ->
                            SegmentedButton(
                                selected = current == h,
                                onClick = { viewModel.setReminders(h, settings.ruleWarnings) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size, RoundedCornerShape(8.dp)),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary,
                                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                                    activeBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                                icon = {},
                                label = { Text(stringResource(R.string.hours_short, h), maxLines = 1) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                SwitchRow(
                    title = stringResource(R.string.reminder_rules_title),
                    subtitle = stringResource(R.string.reminder_rules_text),
                    checked = settings.ruleWarnings,
                    onChange = { on -> viewModel.setReminders(settings.stillClockedInHours, on) },
                )
            }

            // Upgrade to Pro: benefits as bullets, price in the filled button, restore centred
            Section(stringResource(R.string.remove_ads)) {
                if (s.adsRemoved) {
                    Text(stringResource(R.string.remove_ads_owned), color = MaterialTheme.colorScheme.primary)
                } else {
                    listOf(R.string.pro_no_ads, R.string.pro_unlimited_jobs, R.string.pro_free_exports).forEach { res ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(stringResource(R.string.pro_one_time), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    if (s.purchasePending) {
                        Text(stringResource(R.string.purchase_pending))
                    } else {
                        Button(
                            onClick = {
                                val activity = context.findActivity()
                                if (activity == null || !viewModel.buyRemoveAds(activity)) scope.launch { snackbar.showSnackbar(unavailable) }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text(s.price?.let { stringResource(R.string.remove_ads_for, it) } ?: stringResource(R.string.remove_ads)) }
                    }
                }
                TextButton(onClick = { viewModel.restore() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.restore_purchases))
                }
            }

            // Language (per-app language is an Android 13+ system feature)
            Section(stringResource(R.string.settings_language)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    LinkRow(stringResource(R.string.app_language), external = true) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        }
                    }
                } else {
                    Text(stringResource(R.string.language_follows_device), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Backup: icon-labelled export (save out) / import (restore)
            Section(stringResource(R.string.settings_backup)) {
                Text(stringResource(R.string.backup_text), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.exportBackup() }, modifier = Modifier.weight(1f)) {
                        Icon(painterResource(R.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_export))
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }

            // Privacy & about: list rows aligned with the card text, version footer
            Section(stringResource(R.string.settings_about)) {
                if (s.privacyOptionsRequired) {
                    LinkRow(stringResource(R.string.privacy_options)) { context.findActivity()?.let(viewModel::privacyOptions) }
                }
                LinkRow(stringResource(R.string.privacy_policy), external = true) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.pay_disclaimer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun Section(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            content()
        }
    }
}

/** Full-width 48dp row aligned with the card's text; open-in-new for links that leave the app, chevron otherwise. */
@Composable
private fun LinkRow(text: String, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (external) {
            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
