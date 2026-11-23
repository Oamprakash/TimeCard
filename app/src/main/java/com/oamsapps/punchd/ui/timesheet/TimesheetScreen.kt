package com.oamsapps.punchd.ui.timesheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.punchd.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.oamsapps.punchd.domain.engine.DurationParts
import com.oamsapps.punchd.ui.HoursProgress
import com.oamsapps.punchd.ui.PayDisclaimer
import com.oamsapps.punchd.ui.SummaryRow
import com.oamsapps.punchd.ui.formatMoney
import com.oamsapps.punchd.ui.theme.clockOutColor
import androidx.compose.foundation.layout.height
import com.oamsapps.punchd.ads.NativeAdRow
import com.oamsapps.punchd.domain.engine.Timesheet
import com.oamsapps.punchd.domain.engine.TimesheetDay
import com.oamsapps.punchd.domain.model.DayFlag
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.ui.EmptyState
import com.oamsapps.punchd.ui.ErrorState
import com.oamsapps.punchd.ui.JobDot
import com.oamsapps.punchd.ui.TimesheetSkeleton
import com.oamsapps.punchd.ui.formatDayShort
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.formatMonth
import com.oamsapps.punchd.ui.formatTime
import java.time.Duration
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetScreen(
    onAddShift: (LocalDate) -> Unit,
    onEditShift: (Long) -> Unit,
    viewModel: TimesheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openDay by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val saveFailedMsg = stringResource(R.string.save_failed)
    val retryLabel = stringResource(R.string.retry)
    LaunchedEffect(Unit) {
        viewModel.saveFailed.collect { retry ->
            if (snackbar.showSnackbar(saveFailedMsg, actionLabel = retryLabel, withDismissAction = true) == SnackbarResult.ActionPerformed) retry()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    TimesheetMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = state.mode == m,
                            onClick = { viewModel.setMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, TimesheetMode.entries.size),
                        ) { Text(stringResource(if (m == TimesheetMode.WEEK) R.string.week else R.string.month)) }
                    }
                }
                if (state.today !in state.from..state.to) {
                    TextButton(onClick = viewModel::goToToday) { Text(stringResource(R.string.today)) }
                }
            }
            // Same-size buttons on both sides + centred text = the range sits exactly in the middle.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    if (state.mode == TimesheetMode.WEEK) "${formatDayShort(state.from)} – ${formatDayShort(state.to)}"
                    else formatMonth(state.from),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
                }
            }

            val ts = state.timesheet
            if (state.loadError) {
                ErrorState(onRetry = viewModel::reload)
            } else if (state.loading || ts == null) {
                TimesheetSkeleton(rows = if (state.mode == TimesheetMode.WEEK) 7 else 10)
            } else {
                val empty = ts.days.none { it.shifts.isNotEmpty() || it.marks.isNotEmpty() }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                ) {
                    item { TotalsCard(state, ts) }
                    if (empty) {
                        item {
                            // Nothing recorded yet: a short hint, but the days stay visible below.
                            OutlinedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                EmptyState(
                                    title = stringResource(if (state.mode == TimesheetMode.WEEK) R.string.empty_week_title else R.string.empty_month_title),
                                    body = stringResource(R.string.empty_timesheet_text),
                                    compact = true,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                    // Spec: one native ad row per 10 days of entries.
                    var daysWithEntries = 0
                    ts.days.forEachIndexed { index, day ->
                        item(key = day.date.toEpochDay()) {
                            DayRow(day, isToday = day.date == state.today, state = state, onClick = { openDay = day.date })
                            if (index < ts.days.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if (day.shifts.isNotEmpty() || day.marks.isNotEmpty()) {
                            daysWithEntries++
                            if (daysWithEntries % 10 == 0) item(key = "ad-$daysWithEntries") { NativeAdRow() }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onAddShift(state.today.coerceIn(state.from, state.to)) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.add_shift)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    val day = openDay?.let { d -> state.timesheet?.days?.firstOrNull { it.date == d } }
    if (day != null) {
        DaySheet(
            day = day,
            jobs = state.jobs,
            onDismiss = { openDay = null },
            onAddShift = { openDay = null; onAddShift(day.date) },
            onEditShift = { openDay = null; onEditShift(it) },
            onSetMark = { jobId, type, paid -> viewModel.setDayMark(day.date, jobId, type, paid) },
        )
    }
}

/** Same depth as the Clock card: worked vs target with a bar, overtime, leave, estimated pay. */
@Composable
private fun TotalsCard(state: TimesheetUiState, ts: Timesheet) {
    val worked = ts.worked.plus(ts.paidLeave)
    val overtime = ts.overtime.plus(ts.doubleTime)
    val target = state.target
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            SummaryRow(
                stringResource(R.string.worked),
                if (target != null) "${formatHm(worked)} / ${formatHm(target)}" else formatHm(worked),
            )
            if (target != null) {
                val over = when (state.targetKind) {
                    TargetKind.OVERTIME -> overtime > Duration.ZERO
                    else -> worked > target
                }
                Spacer(Modifier.height(12.dp))
                HoursProgress(DurationParts.progress(worked, target), over = over)
                Spacer(Modifier.height(12.dp))
                val remaining = target.minus(worked)
                Text(
                    when {
                        state.targetKind == TargetKind.OVERTIME && !remaining.isNegative ->
                            stringResource(R.string.left_before_overtime, formatHm(remaining))
                        state.targetKind == TargetKind.OVERTIME -> stringResource(R.string.overtime_this_week, formatHm(overtime))
                        !remaining.isNegative -> stringResource(R.string.left_of_schedule, formatHm(remaining))
                        else -> stringResource(R.string.over_schedule, formatHm(remaining.negated()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) clockOutColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (overtime > Duration.ZERO && state.targetKind != TargetKind.OVERTIME) {
                Spacer(Modifier.height(12.dp))
                SummaryRow(stringResource(R.string.overtime), formatHm(overtime), valueColor = clockOutColor())
            }
            if (ts.paidLeave > Duration.ZERO) {
                Spacer(Modifier.height(12.dp))
                SummaryRow(stringResource(R.string.paid_leave), formatHm(ts.paidLeave))
            }
            if (state.pay.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                state.pay.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    SummaryRow(
                        if (state.pay.size == 1) stringResource(R.string.est_gross_pay) else "${stringResource(R.string.est_gross_pay)} (${p.currency})",
                        formatMoney(p.amount, p.currency),
                    )
                }
                Spacer(Modifier.height(12.dp))
                PayDisclaimer()
            }
        }
    }
}

/**
 * One day: date (+ Today badge and tint), right-aligned total or "—", and a chevron so it reads as
 * tappable. Shifts, marks and warnings follow underneath.
 */
@Composable
private fun DayRow(day: TimesheetDay, isToday: Boolean, state: TimesheetUiState, onClick: () -> Unit) {
    val total = day.worked.plus(day.paidLeave)
    val hasEntries = day.shifts.isNotEmpty() || day.marks.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClickLabel = stringResource(R.string.open_day), onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDayShort(day.date),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (isToday) {
                Spacer(Modifier.width(8.dp))
                TodayBadge()
            }
            Spacer(Modifier.weight(1f))
            if (total > Duration.ZERO) {
                Text(
                    formatHm(total),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                val none = stringResource(R.string.no_hours)
                Text(
                    "—",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.semantics { contentDescription = none },
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasEntries) 0.8f else 0.45f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
        day.shifts.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                state.job(row.shift.jobId)?.let { JobDot(it.color) }
                Spacer(Modifier.width(8.dp))
                val end = row.shift.end?.let { formatTime(it, row.shift.zone) } ?: stringResource(R.string.now)
                Text(
                    "${formatTime(row.shift.start, row.shift.zone)} – $end · ${formatHm(row.worked)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (row.breaks > Duration.ZERO) {
                    Text(
                        "  (" + stringResource(R.string.break_short, formatHm(row.breaks)) + ")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        day.marks.forEach { m ->
            Text(
                markLabel(m.type) + (state.job(m.jobId)?.let { " · ${it.name}" } ?: "") + " · " + formatHm(m.paidTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        DayFlags(day.flags)
    }
}

@Composable
private fun TodayBadge() {
    Text(
        stringResource(R.string.today),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DayFlags(flags: Set<DayFlag>) {
    val labels = buildList {
        if (DayFlag.DAILY_OVERTIME in flags || DayFlag.WEEKLY_OVERTIME in flags) add(stringResource(R.string.overtime))
        if (DayFlag.DOUBLE_TIME in flags) add(stringResource(R.string.double_time))
        if (DayFlag.MISSING_BREAK in flags) add(stringResource(R.string.flag_missing_break))
        if (DayFlag.OVER_MAX_HOURS in flags) add(stringResource(R.string.flag_over_max))
    }
    if (labels.isNotEmpty()) {
        Text(
            labels.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun markLabel(type: DayMarkType): String = stringResource(
    when (type) {
        DayMarkType.SICK -> R.string.mark_sick
        DayMarkType.HOLIDAY -> R.string.mark_holiday
        DayMarkType.PAID_LEAVE -> R.string.mark_paid_leave
    }
)

// ---------- Android Studio preview (Split / Design view) ----------

@androidx.compose.ui.tooling.preview.Preview(name = "Timesheet week", showBackground = true, heightDp = 1000)
@Composable
private fun TimesheetPreview() = com.oamsapps.punchd.ui.theme.PunchdTheme(dark = false) {
    val zone = java.time.ZoneId.of("America/New_York")
    val from = LocalDate.of(2026, 11, 22) // Sunday
    fun shift(day: Int, h: Long) = com.oamsapps.punchd.domain.model.WorkShift(
        day.toLong(), 1, from.plusDays(day.toLong()).atTime(9, 0).atZone(zone).toInstant(),
        from.plusDays(day.toLong()).atTime(9, 0).atZone(zone).toInstant().plusSeconds(h * 3600), zone,
    )
    val job = com.oamsapps.punchd.domain.engine.JobInfo(1, com.oamsapps.punchd.domain.model.RulePresets.US_FEDERAL, java.math.BigDecimal("20"))
    val ts = com.oamsapps.punchd.domain.engine.TimesheetBuilder.build(
        listOf(job), listOf(shift(1, 8), shift(2, 9)), emptyList(), java.time.DayOfWeek.SUNDAY,
        from, from.plusDays(6), java.time.Instant.parse("2030-01-01T00:00:00Z"),
    )
    val state = TimesheetUiState(
        loading = false, from = from, to = from.plusDays(6), today = from.plusDays(2),
        jobs = listOf(com.oamsapps.punchd.ui.JobLabel(1, "Primary job", 0xFF1E88E5.toInt(), "USD", null)),
        timesheet = ts, target = java.time.Duration.ofHours(40), targetKind = TargetKind.OVERTIME,
        pay = listOf(CurrencyAmount("USD", ts.perJob.single().grossPay)),
    )
    androidx.compose.material3.Surface {
        Column(Modifier.padding(16.dp)) {
            TotalsCard(state, ts)
            Spacer(Modifier.height(12.dp))
            ts.days.forEach { DayRow(it, isToday = it.date == state.today, state = state, onClick = {}) }
        }
    }
}
