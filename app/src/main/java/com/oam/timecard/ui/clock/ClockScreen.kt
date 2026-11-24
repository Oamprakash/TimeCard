package com.oam.timecard.ui.clock

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oam.timecard.R
import com.oam.timecard.domain.engine.DurationParts
import com.oam.timecard.domain.model.DayFlag
import com.oam.timecard.domain.model.PresetKind
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Duration
import java.util.Currency
import java.util.Locale

private val JOB_COLORS = listOf(0xFF1E88E5, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA, 0xFFE53935, 0xFF00897B).map { it.toInt() }
private val CLOCK_IN_GREEN = Color(0xFF2E7D32)
private val CLOCK_OUT_RED = Color(0xFFC62828)

@Composable
fun ClockScreen(viewModel: ClockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showAddJob by rememberSaveable { mutableStateOf(false) }

    val limitMsg = stringResource(R.string.job_limit)
    val invalidMsg = stringResource(R.string.invalid_job)
    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            snackbar.showSnackbar(if (it == ClockMessage.JOB_LIMIT) limitMsg else invalidMsg)
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
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.jobs.isEmpty() -> JobForm(
                title = stringResource(R.string.first_job_title),
                onSave = { name, rate, currency, preset ->
                    viewModel.addJob(name, rate, currency, preset, JOB_COLORS.first())
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
                    onSave = { name, rate, currency, preset ->
                        viewModel.addJob(name, rate, currency, preset, JOB_COLORS[state.jobs.size % JOB_COLORS.size])
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
) {
    val running = state.running
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        // Status + live timer.
        Text(
            text = when {
                running == null -> stringResource(R.string.not_clocked_in)
                running.onBreak -> stringResource(R.string.on_break_label)
                else -> stringResource(R.string.working_label)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = DurationParts.clock(running?.worked ?: Duration.ZERO),
            fontSize = 52.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        if (running?.onBreak == true) {
            Text(stringResource(R.string.break_so_far, formatHm(running.breakTotal)), style = MaterialTheme.typography.bodyMedium)
        }

        // The big button. Nothing (no ad, no dialog) is ever placed before this tap.
        Button(
            onClick = if (running == null) onClockIn else onClockOut,
            enabled = !busy && state.selectedJobId != null,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = if (running == null) CLOCK_IN_GREEN else CLOCK_OUT_RED),
            modifier = Modifier.size(200.dp),
        ) {
            Text(
                stringResource(if (running == null) R.string.clock_in else R.string.clock_out),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (running != null) {
            OutlinedButton(onClick = onToggleBreak, enabled = !busy, modifier = Modifier.fillMaxWidth(0.7f).height(52.dp)) {
                Text(stringResource(if (running.onBreak) R.string.end_break else R.string.start_break))
            }
        }

        TotalsCard(state)

        Text(
            stringResource(R.string.pay_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Sprint 4: adaptive banner goes here, below everything, hidden while a dialog is open.
    }
}

@Composable
private fun TotalsCard(state: ClockUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.today))
                Text(formatHm(state.today), fontWeight = FontWeight.SemiBold)
            }
            if (DayFlag.MISSING_BREAK in state.todayFlags) {
                Text(stringResource(R.string.flag_missing_break), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (DayFlag.OVER_MAX_HOURS in state.todayFlags) {
                Text(stringResource(R.string.flag_over_max), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.this_week))
                Text(formatHm(state.week), fontWeight = FontWeight.SemiBold)
            }
            state.weeklyThreshold?.let { threshold ->
                LinearProgressIndicator(
                    progress = { DurationParts.progress(state.week, threshold) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (state.weekOvertime > Duration.ZERO) CLOCK_OUT_RED else MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.week_of_threshold, formatHm(minOf(state.week, threshold)), formatHm(threshold)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.weekOvertime > Duration.ZERO) {
                Text(stringResource(R.string.overtime_this_week, formatHm(state.weekOvertime)), color = CLOCK_OUT_RED)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.est_pay))
                Text(formatMoney(state.weekPay, state.currency), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Shared by first-run and the add-job dialog. Full onboarding arrives in Sprint 5. */
@Composable
private fun JobForm(
    title: String,
    onSave: (name: String, rate: String, currency: String, preset: PresetKind) -> Unit,
    onCancel: (() -> Unit)?,
) {
    val locale = Locale.getDefault()
    val defaultCurrency = remember { runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("USD") }
    var name by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
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
            Button(onClick = { onSave(name, rate, defaultCurrency, preset) }) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun formatHm(d: Duration): String {
    val p = DurationParts.of(d)
    return stringResource(R.string.hours_minutes, p.hours, p.minutes)
}

private fun formatMoney(amount: BigDecimal, currency: String): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        runCatching { this.currency = Currency.getInstance(currency) }
    }.format(amount)
