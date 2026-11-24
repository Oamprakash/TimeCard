package com.oamsapps.punchd.ui.reports

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
import androidx.compose.material3.SnackbarResult
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
import com.oamsapps.punchd.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.oamsapps.punchd.ui.PayDisclaimer
import com.oamsapps.punchd.ui.SummaryRow
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.domain.model.JobPeriodSummary
import com.oamsapps.punchd.ui.theme.clockOutColor
import com.oamsapps.punchd.ui.theme.selectorContainerColor
import com.oamsapps.punchd.ads.NativeAdCard
import com.oamsapps.punchd.export.ExportFormat
import com.oamsapps.punchd.ui.findActivity
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.domain.engine.PayPeriodType
import com.oamsapps.punchd.ui.EmptyState
import com.oamsapps.punchd.ui.ErrorState
import com.oamsapps.punchd.ui.JobDot
import com.oamsapps.punchd.ui.ReportsSkeleton
import com.oamsapps.punchd.ui.PayPeriodDialog
import com.oamsapps.punchd.ui.periodTypeLabel
import com.oamsapps.punchd.ui.formatDateMedium
import com.oamsapps.punchd.ui.formatDayShort
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.formatMoney
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
    var jobMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val failed = stringResource(R.string.export_failed)
    val retryLabel = stringResource(R.string.retry)
    val billingUnavailable = stringResource(R.string.billing_unavailable)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is ExportEvent.Share -> runCatching { context.startActivity(e.intent) }
                is ExportEvent.Failed -> if (
                    snackbar.showSnackbar(failed, actionLabel = retryLabel, withDismissAction = true) == SnackbarResult.ActionPerformed
                ) viewModel.export(e.format) // no second ad: the user already watched one
            }
        }
    }

    val period = state.period
    if (state.loadError) {
        ErrorState(onRetry = viewModel::reload)
        return
    }
    if (state.loading || period == null) {
        ReportsSkeleton()
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Period navigator: range centred between equal arrows, period type underneath.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
                    }
                    Text(
                        "${formatDayShort(period.start)} – ${formatDayShort(period.endInclusive)}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::next) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
                    }
                }

                // Selectors: clearly tappable (tinted fill + ⌄), balanced as a pair.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.config?.let { cfg ->
                        SelectorChip(
                            label = periodTypeLabel(cfg.type),
                            a11y = stringResource(R.string.pay_period_a11y, periodTypeLabel(cfg.type)),
                            onClick = { editPeriod = true },
                        )
                    }
                    if (state.jobOptions.size > 1) {
                        Box {
                            val jobLabel = state.selectedJob?.name ?: stringResource(R.string.all_jobs)
                            SelectorChip(
                                label = jobLabel,
                                a11y = stringResource(R.string.job_filter_a11y, jobLabel),
                                onClick = { jobMenu = true },
                                leading = state.selectedJob?.let { j -> { JobDot(j.color) } },
                            )
                            DropdownMenu(expanded = jobMenu, onDismissRequest = { jobMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.all_jobs)) },
                                    onClick = { viewModel.selectJob(null); jobMenu = false },
                                )
                                state.jobOptions.forEach { j ->
                                    DropdownMenuItem(
                                        text = { Text(j.name) },
                                        leadingIcon = { JobDot(j.color, 12.dp) },
                                        onClick = { viewModel.selectJob(j.id); jobMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!state.isCurrent) TextButton(onClick = viewModel::current) { Text(stringResource(R.string.current_period)) }
                }

                if (state.jobOptions.isEmpty()) {
                    EmptyState(title = stringResource(R.string.no_jobs_title), body = stringResource(R.string.no_jobs_yet))
                }

                // 1) Grand total first, visually stronger than the job cards.
                if (state.jobOptions.size > 1 && state.selectedJobId == null) {
                    TotalsCard(state)
                }

                // 2) Jobs with hours (most first), 3) zero-hour jobs collapsed into one quiet card.
                val active = state.jobs.filter { it.hasHours }
                val idle = state.jobs.filterNot { it.hasHours }
                if (state.jobOptions.isNotEmpty() && active.isEmpty()) {
                    Text(stringResource(R.string.empty_period_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                active.forEach { JobCard(it) }
                if (idle.isNotEmpty()) IdleJobsCard(idle)

                if (active.isNotEmpty()) PayDisclaimer()

                // Native ad in the content (replaces the bottom banner). Extra gap keeps it clear of the
                // export buttons so it can't be tapped by mistake.
                if (state.jobs.isNotEmpty()) NativeAdCard(Modifier.padding(bottom = 12.dp))

                // 4) Export: outlined (an action, not data), share icons, one-line hint.
                if (state.jobs.isNotEmpty()) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.export_timesheet), style = MaterialTheme.typography.titleMedium)
                            state.selectedJob?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(stringResource(R.string.export_only_job, it.name), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(ExportFormat.PDF to R.string.export_pdf, ExportFormat.XLSX to R.string.export_excel, ExportFormat.CSV to R.string.export_csv)
                                    .forEach { (format, label) ->
                                        OutlinedButton(
                                            onClick = {
                                                if (viewModel.needsRewardedAd(format)) askAdFor = format else viewModel.export(format)
                                            },
                                            enabled = exporting == null,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            if (exporting == format) {
                                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(stringResource(label))
                                            }
                                        }
                                    }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.export_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // The column's 16dp padding leaves a clean margin above the navigation bar.
            }
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
private fun SelectorChip(label: String, a11y: String, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leading,
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = selectorContainerColor()),
        border = null,
        modifier = Modifier.semantics { contentDescription = a11y },
    )
}

/** All jobs: brand border + tint, combined hours, and pay per currency (code as label, symbol in the value). */
@Composable
private fun TotalsCard(state: ReportsUiState) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.outlinedCardColors(containerColor = com.oamsapps.punchd.ui.theme.highlightContainerColor()),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.all_jobs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SummaryRow(stringResource(R.string.total_hours), formatHm(state.totalHours))
            if (state.totalOvertime > Duration.ZERO) {
                Spacer(Modifier.height(8.dp))
                SummaryRow(stringResource(R.string.overtime), formatHm(state.totalOvertime), valueColor = clockOutColor())
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            state.totals.forEachIndexed { i, t ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                SummaryRow(t.currency, formatMoney(t.gross, t.currency))
            }
        }
    }
}

@Composable
private fun JobCard(r: JobReport) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JobDot(r.job.color, 12.dp)
                Spacer(Modifier.width(8.dp))
                Text(r.job.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(formatHm(r.hours), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            }
            Line(stringResource(R.string.regular), formatHm(r.summary.regular))
            Line(stringResource(R.string.overtime), formatHm(r.summary.overtime))
            if (r.summary.doubleTime > Duration.ZERO) Line(stringResource(R.string.double_time), formatHm(r.summary.doubleTime))
            if (r.summary.paidLeave > Duration.ZERO) Line(stringResource(R.string.paid_leave), formatHm(r.summary.paidLeave))
            r.scheduled?.let { Line(stringResource(R.string.worked_vs_scheduled), "${formatHm(r.hours)} / ${formatHm(it)}") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Line(stringResource(R.string.gross_pay_estimate), formatMoney(r.summary.grossPay, r.job.currency), bold = true)
        }
    }
}

/** Zero-hour jobs: one quiet card instead of a full card of zeros each. */
@Composable
private fun IdleJobsCard(idle: List<JobReport>) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.no_hours_period), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            idle.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JobDot(r.job.color)
                    Spacer(Modifier.width(8.dp))
                    Text(r.job.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Reports cards", showBackground = true, heightDp = 900)
@Composable
private fun ReportsCardsPreview() = com.oamsapps.punchd.ui.theme.PunchdTheme(dark = false) {
    val day = java.time.LocalDate.of(2026, 11, 16)
    fun report(id: Long, name: String, color: Long, cur: String, h: Long, ot: Long, pay: String) = JobReport(
        job = JobLabel(id, name, color.toInt(), cur, null),
        summary = JobPeriodSummary(id, day, day.plusDays(13), emptyList(), Duration.ofHours(h + ot), Duration.ofHours(h),
            Duration.ofHours(ot), Duration.ZERO, Duration.ZERO, java.math.BigDecimal(pay)),
        scheduled = if (id == 1L) Duration.ofHours(80) else null,
    )
    val warehouse = report(1, "Warehouse", 0xFF1B873F, "USD", 76, 6, "1593.00")
    val tutoring = report(2, "Tutoring", 0xFF1565C0, "INR", 1, 0, "19.64")
    val idle = report(3, "Cafe", 0xFFEF6C00, "USD", 0, 0, "0")
    val state = ReportsUiState(
        jobs = listOf(warehouse, tutoring, idle),
        totals = listOf(CurrencyTotal("USD", java.math.BigDecimal("1593.00")), CurrencyTotal("INR", java.math.BigDecimal("19.64"))),
        totalHours = Duration.ofHours(83), totalOvertime = Duration.ofHours(6),
        jobOptions = listOf(warehouse.job, tutoring.job, idle.job),
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorChip("Biweekly", "", {})
            SelectorChip(stringResource(R.string.all_jobs), "", {})
        }
        TotalsCard(state)
        JobCard(warehouse)
        JobCard(tutoring)
        IdleJobsCard(listOf(idle))
    }
}
