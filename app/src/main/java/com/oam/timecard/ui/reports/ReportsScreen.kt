package com.oam.timecard.ui.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oam.timecard.R
import com.oam.timecard.ads.AdBanner
import com.oam.timecard.export.ExportFormat
import com.oam.timecard.ui.findActivity
import com.oam.timecard.domain.engine.PayPeriodConfig
import com.oam.timecard.domain.engine.PayPeriodType
import com.oam.timecard.ui.JobDot
import com.oam.timecard.ui.formatDateMedium
import com.oam.timecard.ui.formatDayShort
import com.oam.timecard.ui.formatHm
import com.oam.timecard.ui.formatMoney
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ReportsScreen(viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val price by viewModel.removeAdsPrice.collectAsStateWithLifecycle()
    var editPeriod by remember { mutableStateOf(false) }
    var askAdFor by remember { mutableStateOf<ExportFormat?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.export_failed)
    val billingUnavailable = stringResource(R.string.billing_unavailable)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is ExportEvent.Share -> runCatching { context.startActivity(e.intent) }
                ExportEvent.Failed -> snackbar.showSnackbar(failed)
            }
        }
    }

    val period = state.period
    if (state.loading || period == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${formatDayShort(period.start)} – ${formatDayShort(period.endInclusive)}", style = MaterialTheme.typography.titleMedium)
                        state.config?.let { Text(periodTypeLabel(it.type), style = MaterialTheme.typography.bodySmall) }
                    }
                    IconButton(onClick = viewModel::next) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.isCurrent) OutlinedButton(onClick = viewModel::current) { Text(stringResource(R.string.current_period)) }
                    OutlinedButton(onClick = { editPeriod = true }) { Text(stringResource(R.string.pay_period_settings)) }
                }

                if (state.jobs.isEmpty()) {
                    Text(stringResource(R.string.no_jobs_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                state.jobs.forEach { r ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                JobDot(r.job.color, 12.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(r.job.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Line(stringResource(R.string.regular), formatHm(r.summary.regular))
                            Line(stringResource(R.string.overtime), formatHm(r.summary.overtime))
                            if (r.summary.doubleTime > Duration.ZERO) Line(stringResource(R.string.double_time), formatHm(r.summary.doubleTime))
                            if (r.summary.paidLeave > Duration.ZERO) Line(stringResource(R.string.paid_leave), formatHm(r.summary.paidLeave))
                            r.scheduled?.let { Line(stringResource(R.string.worked_vs_scheduled), "${formatHm(r.summary.worked.plus(r.summary.paidLeave))} / ${formatHm(it)}") }
                            HorizontalDivider()
                            Line(stringResource(R.string.gross_pay_estimate), formatMoney(r.summary.grossPay, r.job.currency), bold = true)
                        }
                    }
                }

                if (state.jobs.size > 1) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.all_jobs), style = MaterialTheme.typography.titleMedium)
                            state.totals.forEach { Line(it.currency, formatMoney(it.gross, it.currency), bold = true) }
                        }
                    }
                }

                if (state.jobs.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.export_timesheet), style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(ExportFormat.PDF to R.string.export_pdf, ExportFormat.XLSX to R.string.export_excel, ExportFormat.CSV to R.string.export_csv)
                                    .forEach { (format, label) ->
                                        OutlinedButton(
                                            onClick = {
                                                if (viewModel.needsRewardedAd(format)) askAdFor = format else viewModel.export(format)
                                            },
                                            enabled = exporting == null,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            if (exporting == format) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            else Text(stringResource(label))
                                        }
                                    }
                            }
                            Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(
                    stringResource(R.string.pay_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Banner at the bottom of Reports, hidden while a dialog is open.
            AdBanner(visible = !editPeriod && askAdFor == null)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    askAdFor?.let { format ->
        AlertDialog(
            onDismissRequest = { askAdFor = null },
            title = { Text(stringResource(if (format == ExportFormat.PDF) R.string.export_pdf_title else R.string.export_excel_title)) },
            text = { Text(stringResource(R.string.export_ad_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    askAdFor = null
                    context.findActivity()?.let { viewModel.watchAdThenExport(it, format) }
                }) { Text(stringResource(R.string.watch_video)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        askAdFor = null
                        val activity = context.findActivity()
                        if (activity == null || !viewModel.buyRemoveAds(activity)) {
                            scope.launch { snackbar.showSnackbar(billingUnavailable) }
                        }
                    }) { Text(price?.let { stringResource(R.string.remove_ads_for, it) } ?: stringResource(R.string.remove_ads)) }
                    TextButton(onClick = { askAdFor = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    if (editPeriod) {
        state.config?.let { cfg ->
            PayPeriodDialog(
                initial = cfg,
                initialWeekStart = state.weekStart,
                onDismiss = { editPeriod = false },
                onSave = { c, ws -> viewModel.savePeriodSettings(c, ws); editPeriod = false },
            )
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun periodTypeLabel(type: PayPeriodType) = stringResource(
    when (type) {
        PayPeriodType.WEEKLY -> R.string.period_weekly
        PayPeriodType.BIWEEKLY -> R.string.period_biweekly
        PayPeriodType.SEMI_MONTHLY -> R.string.period_semi_monthly
        PayPeriodType.MONTHLY -> R.string.period_monthly
    }
)

/** Pay period and week start. Moves to the Settings tab in Sprint 5. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayPeriodDialog(
    initial: PayPeriodConfig,
    initialWeekStart: DayOfWeek,
    onDismiss: () -> Unit,
    onSave: (PayPeriodConfig, DayOfWeek) -> Unit,
) {
    var type by remember { mutableStateOf(initial.type) }
    var anchor by remember { mutableStateOf(initial.anchor) }
    var weekStart by remember { mutableStateOf(initialWeekStart) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pay_period_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.pay_period), style = MaterialTheme.typography.titleSmall)
                PayPeriodType.entries.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(periodTypeLabel(t)) })
                }
                if (type != PayPeriodType.SEMI_MONTHLY) {
                    OutlinedButton(onClick = { pickDate = true }) {
                        Text(stringResource(R.string.period_starts_on, formatDateMedium(anchor)))
                    }
                } else {
                    Text(stringResource(R.string.semi_monthly_hint), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.week_starts_on), style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).forEach { d ->
                        FilterChip(
                            selected = weekStart == d,
                            onClick = { weekStart = d },
                            label = { Text(d.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(PayPeriodConfig(type, anchor), weekStart) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { anchor = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }
}
