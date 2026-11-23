package com.oamsapps.punchd.ui.clock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.punchd.R
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.StrokeCap
import com.oamsapps.punchd.ui.theme.clockInColor
import com.oamsapps.punchd.ui.theme.clockOutColor
import com.oamsapps.punchd.ads.AdBanner
import com.oamsapps.punchd.ui.ClockSkeleton
import com.oamsapps.punchd.ui.ErrorState
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.formatMoney
import com.oamsapps.punchd.domain.engine.DurationParts
import com.oamsapps.punchd.domain.model.DayFlag
import com.oamsapps.punchd.domain.model.PresetKind
import java.time.Duration
import java.util.Currency
import java.util.Locale

private val JOB_COLORS = listOf(0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFFE53935, 0xFF00897B).map { it.toInt() }

@Composable
fun ClockScreen(viewModel: ClockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddJob by rememberSaveable { mutableStateOf(false) }

    val limitMsg = stringResource(R.string.job_limit)
    val invalidMsg = stringResource(R.string.invalid_job)
    val saveFailedMsg = stringResource(R.string.save_failed)
    val punchFailedMsg = stringResource(R.string.punch_failed)
    val retryLabel = stringResource(R.string.retry)
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                ClockMessage.JOB_LIMIT -> snackbar.showSnackbar(limitMsg)
                ClockMessage.INVALID_JOB -> snackbar.showSnackbar(invalidMsg)
                ClockMessage.SAVE_FAILED -> snackbar.showSnackbar(saveFailedMsg)
                // Stays until dismissed: a missed punch matters more than a stray snackbar.
                ClockMessage.PUNCH_FAILED -> if (
                    snackbar.showSnackbar(punchFailedMsg, actionLabel = retryLabel, withDismissAction = true,
                        duration = SnackbarDuration.Indefinite) == SnackbarResult.ActionPerformed
                ) viewModel.retryPunch()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationPermissionResult()
    }
    // The punch is never delayed by the permission dialog: clock in first, then ask.
    val onClockIn = {
        viewModel.clockIn()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> ClockSkeleton()
            state.loadError -> ErrorState(onRetry = viewModel::reload)
            state.jobs.isEmpty() -> JobForm(
                title = stringResource(R.string.first_job_title),
                onSave = { name, rate, currency, preset, scheduled ->
                    viewModel.addJob(name, rate, currency, preset, JOB_COLORS.first(), scheduled)
                },
                onCancel = null,
            )
            else -> ClockDashboard(
                state = state,
                busy = busy,
                onSelectJob = viewModel::selectJob,
                onAddJob = { showAddJob = true },
                onClockIn = onClockIn,
                onClockOut = viewModel::clockOut,
                onToggleBreak = viewModel::toggleBreak,
                showBanner = !showAddJob,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showAddJob) {
        AlertDialog(
            onDismissRequest = { showAddJob = false },
            confirmButton = {},
            text = {
                JobForm(
                    title = stringResource(R.string.add_job),
                    onSave = { name, rate, currency, preset, scheduled ->
                        viewModel.addJob(name, rate, currency, preset, JOB_COLORS[state.jobs.size % JOB_COLORS.size], scheduled)
                        showAddJob = false
                    },
                    onCancel = { showAddJob = false },
                )
            },
        )
    }
}

@Composable
private fun ClockDashboard(
    state: ClockUiState,
    busy: Boolean,
    onSelectJob: (Long) -> Unit,
    onAddJob: () -> Unit,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    onToggleBreak: () -> Unit,
    showBanner: Boolean,
) {
    val running = state.running
    // Content scrolls; the banner is anchored at the bottom, right above the navigation bar.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Job selector — locked while clocked in.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.jobs.forEach { job ->
                    FilterChip(
                        selected = job.id == state.selectedJobId,
                        onClick = { onSelectJob(job.id) },
                        enabled = running == null || job.id == state.selectedJobId,
                        label = { Text(job.name) },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(job.color), CircleShape)) },
                    )
                }
                if (running == null) AssistChip(onClick = onAddJob, label = { Text("+ " + stringResource(R.string.add_job)) })
            }

            Spacer(Modifier.height(28.dp))

            // Status + live timer. Tabular figures keep the digits from jittering; no gaps around the colons.
            Text(
                text = when {
                    running == null -> stringResource(R.string.not_clocked_in)
                    running.onBreak -> stringResource(R.string.on_break_label)
                    else -> stringResource(R.string.working_label)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = DurationParts.clock(running?.worked ?: Duration.ZERO),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-0.5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (running?.onBreak == true) {
                Text(stringResource(R.string.break_so_far, formatHm(running.breakTotal)), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            ClockButton(
                clockedIn = running != null,
                enabled = !busy && state.selectedJobId != null,
                onClick = if (running == null) onClockIn else onClockOut,
            )

            if (running != null) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onToggleBreak, enabled = !busy, modifier = Modifier.fillMaxWidth(0.7f).height(52.dp)) {
                    Text(stringResource(if (running.onBreak) R.string.end_break else R.string.start_break))
                }
            }

            Spacer(Modifier.height(28.dp))
            TotalsCard(state)
        }
        AdBanner(visible = showBanner)
    }
}

/**
 * The big round button: a soft halo ring behind it and real elevation, so it reads as tappable.
 * Nothing (no ad, no dialog) is ever placed before this tap.
 */
@Composable
private fun ClockButton(clockedIn: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = if (clockedIn) clockOutColor() else clockInColor()
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(236.dp)) {
        Box(Modifier.matchParentSize().background(color.copy(alpha = if (enabled) 0.14f else 0.06f), CircleShape))
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp, disabledElevation = 0.dp),
            modifier = Modifier.size(200.dp),
        ) {
            Text(
                stringResource(if (clockedIn) R.string.clock_out else R.string.clock_in),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TotalsCard(state: ClockUiState) {
    val overtimeColor = clockOutColor()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            TotalRow(stringResource(R.string.today), formatHm(state.today))
            if (DayFlag.MISSING_BREAK in state.todayFlags) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.flag_missing_break), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (DayFlag.OVER_MAX_HOURS in state.todayFlags) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.flag_over_max), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            TotalRow(stringResource(R.string.this_week), formatHm(state.week))
            state.weeklyThreshold?.let { threshold ->
                // Equal space above and below the bar.
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { DurationParts.progress(state.week, threshold) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (state.weekOvertime > Duration.ZERO) overtimeColor else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.week_of_threshold, formatHm(minOf(state.week, threshold)), formatHm(threshold)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.weekOvertime > Duration.ZERO) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.overtime_this_week, formatHm(state.weekOvertime)), color = overtimeColor,
                    style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
            TotalRow(stringResource(R.string.est_pay), formatMoney(state.weekPay, state.currency))

            // The disclaimer lives with the number it qualifies, quietly.
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.pay_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
    }
}

/** Shared by first-run and the add-job dialog. Full onboarding arrives in Sprint 5. */
@Composable
private fun JobForm(
    title: String,
    onSave: (name: String, rate: String, currency: String, preset: PresetKind, scheduledHours: String) -> Unit,
    onCancel: (() -> Unit)?,
) {
    val locale = Locale.getDefault()
    val defaultCurrency = remember { runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD") }
    var name by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var scheduled by rememberSaveable { mutableStateOf("") }
    var preset by rememberSaveable {
        mutableStateOf(
            when (locale.country) {
                "US" -> PresetKind.US_FEDERAL
                "DE" -> PresetKind.GERMANY
                else -> PresetKind.CUSTOM
            }
        )
    }

    Column(Modifier.fillMaxWidth().padding(if (onCancel == null) 24.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.job_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            rate, { rate = it },
            label = { Text(stringResource(R.string.hourly_rate, defaultCurrency)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            scheduled, { scheduled = it },
            label = { Text(stringResource(R.string.scheduled_hours_optional)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.rules), style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                PresetKind.US_FEDERAL to R.string.preset_us,
                PresetKind.CALIFORNIA to R.string.preset_ca,
                PresetKind.GERMANY to R.string.preset_de,
                PresetKind.CUSTOM to R.string.preset_custom,
            ).forEach { (kind, label) ->
                FilterChip(selected = preset == kind, onClick = { preset = kind }, label = { Text(stringResource(label)) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { onSave(name, rate, defaultCurrency, preset, scheduled) }) { Text(stringResource(R.string.save)) }
        }
    }
}

// ---------- Android Studio previews (Split / Design view) ----------

private fun previewState(running: Boolean) = ClockUiState(
    loading = false,
    jobs = listOf(
        JobUi(1, "Primary job", 0xFF1E88E5.toInt(), "USD", Duration.ofHours(40)),
        JobUi(2, "Test job", 0xFF43A047.toInt(), "USD", Duration.ofHours(40)),
    ),
    selectedJobId = 1,
    running = if (running) com.oamsapps.punchd.domain.engine.RunningSnapshot(
        shiftId = 1, jobId = 1, start = java.time.Instant.now().minusSeconds(3 * 3600),
        worked = Duration.ofSeconds(2 * 3600 + 47 * 60 + 12), onBreak = false, breakStart = null,
        breakTotal = Duration.ofMinutes(13),
    ) else null,
    today = if (running) Duration.ofSeconds(2 * 3600 + 47 * 60) else Duration.ZERO,
    week = Duration.ofHours(31).plusMinutes(20),
    weeklyThreshold = Duration.ofHours(40),
    weekPay = java.math.BigDecimal("626.67"),
    currency = "USD",
)

@androidx.compose.ui.tooling.preview.Preview(name = "Clock · light", showBackground = true, heightDp = 860)
@Composable
private fun ClockPreviewLight() = com.oamsapps.punchd.ui.theme.PunchdTheme(dark = false) {
    androidx.compose.material3.Surface {
        ClockDashboard(previewState(false), busy = false, {}, {}, {}, {}, {}, showBanner = true)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Clock · clocked in · dark", showBackground = true, heightDp = 900)
@Composable
private fun ClockPreviewDarkRunning() = com.oamsapps.punchd.ui.theme.PunchdTheme(dark = true) {
    androidx.compose.material3.Surface {
        ClockDashboard(previewState(true), busy = false, {}, {}, {}, {}, {}, showBanner = true)
    }
}
