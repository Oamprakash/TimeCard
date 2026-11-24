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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import com.oamsapps.punchd.ui.theme.selectorContainerColor
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
            // Jobs & rules: tappable rows (56dp, chevron, dividers) + "+ Add job"
            Section(stringResource(R.string.settings_jobs)) {
                s.jobs.forEach { j ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onAddJob) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_job))
                }
            }

            // Pay period: outlined "Change" as the card header action
            Section(
                title = stringResource(R.string.pay_period),
                action = {
                    OutlinedButton(onClick = { editPeriod = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.change))
                    }
                },
            ) {
                Text(periodTypeLabel(settings.payPeriod.type), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (settings.payPeriod.type != PayPeriodType.SEMI_MONTHLY) {
                    Text(stringResource(R.string.period_starts_on_long, formatDateMedium(settings.payPeriod.anchor)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.week_starts_on) + ": " + settings.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Reminders: four short chips (6h–12h) fit on one line; divider before rule warnings
            Section(stringResource(R.string.settings_reminders)) {
                SwitchRow(
                    title = stringResource(R.string.reminder_still_title),
                    subtitle = stringResource(R.string.reminder_still_text),
                    checked = settings.stillClockedInHours != null,
                    onChange = { on -> viewModel.setReminders(if (on) DEFAULT_STILL_CLOCKED_IN_HOURS else null, settings.ruleWarnings) },
                )
                settings.stillClockedInHours?.let { current ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 8, 10, 12).forEach { h ->
                            val selected = current == h
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setReminders(h, settings.ruleWarnings) },
                                label = { Text(stringResource(R.string.hours_short, h), style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = selectorContainerColor()),
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(
                    title = stringResource(R.string.reminder_rules_title),
                    subtitle = stringResource(R.string.reminder_rules_text),
                    checked = settings.ruleWarnings,
                    onChange = { on -> viewModel.setReminders(settings.stillClockedInHours, on) },
                )
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
                    OutlinedButton(onClick = { viewModel.exportBackup() }) {
                        Icon(painterResource(R.drawable.ic_upload), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_export))
                    }
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    ) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_import))
                    }
                }
            }

            // Upgrade to Pro: crown title, one-line value, price in the filled button
            Section(
                title = stringResource(R.string.remove_ads),
                icon = { Icon(painterResource(R.drawable.ic_crown), contentDescription = null, modifier = Modifier.size(22.dp)) },
            ) {
                if (s.adsRemoved) {
                    Text(stringResource(R.string.remove_ads_owned), color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(stringResource(R.string.pro_benefits), style = MaterialTheme.typography.bodyMedium)
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
                TextButton(onClick = { viewModel.restore() }) { Text(stringResource(R.string.restore_purchases)) }
            }

            // Privacy & about: list rows aligned with the card text, version footer
            Section(stringResource(R.string.settings_about)) {
                if (s.privacyOptionsRequired) {
                    LinkRow(stringResource(R.string.privacy_options)) { context.findActivity()?.let(viewModel::privacyOptions) }
                }
                LinkRow(stringResource(R.string.privacy_policy)) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
                }
                HorizontalDivider(Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                icon?.let { it(); Spacer(Modifier.width(10.dp)) }
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
