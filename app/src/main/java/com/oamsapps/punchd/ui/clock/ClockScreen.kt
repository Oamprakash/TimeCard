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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.punchd.R
import androidx.compose.material3.HorizontalDivider
import com.oamsapps.punchd.ui.theme.clockInColor
import com.oamsapps.punchd.ui.theme.clockOutColor
import com.oamsapps.punchd.ads.AdBanner
import com.oamsapps.punchd.ui.ClockSkeleton
import com.oamsapps.punchd.ui.EmptyState
import com.oamsapps.punchd.ui.ErrorState
import com.oamsapps.punchd.ui.HoursProgress
import com.oamsapps.punchd.ui.PayDisclaimer
import com.oamsapps.punchd.ui.SummaryRow
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.formatMoney
import com.oamsapps.punchd.domain.engine.DurationParts
import com.oamsapps.punchd.domain.model.DayFlag
import java.time.Duration

@Composable
fun ClockScreen(onAddJob: () -> Unit, viewModel: ClockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val saveFailedMsg = stringResource(R.string.save_failed)
    val punchFailedMsg = stringResource(R.string.punch_failed)
    val retryLabel = stringResource(R.string.retry)
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
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
            state.jobs.isEmpty() -> EmptyState(
                title = stringResource(R.string.no_jobs_title),
                body = stringResource(R.string.clock_no_jobs),
                actionLabel = stringResource(R.string.add_job),
                onAction = onAddJob,
            )
            else -> ClockDashboard(
                state = state,
                busy = busy,
                onSelectJob = viewModel::selectJob,
                onAddJob = onAddJob,
                onClockIn = onClockIn,
                onClockOut = viewModel::clockOut,
                onToggleBreak = viewModel::toggleBreak,
                showBanner = true,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
            SummaryRow(stringResource(R.string.today), formatHm(state.today))
            if (DayFlag.MISSING_BREAK in state.todayFlags) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.flag_missing_break), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (DayFlag.OVER_MAX_HOURS in state.todayFlags) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.flag_over_max), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            SummaryRow(stringResource(R.string.this_week), formatHm(state.week))
            state.weeklyThreshold?.let { threshold ->
                // Equal space above and below the bar.
                Spacer(Modifier.height(12.dp))
                HoursProgress(DurationParts.progress(state.week, threshold), over = state.weekOvertime > Duration.ZERO)
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
            SummaryRow(stringResource(R.string.est_pay), formatMoney(state.weekPay, state.currency))

            // The disclaimer lives with the number it qualifies, quietly.
            Spacer(Modifier.height(12.dp))
            PayDisclaimer()
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
