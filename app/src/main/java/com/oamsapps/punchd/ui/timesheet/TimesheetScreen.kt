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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.oamsapps.punchd.ads.NativeAdRow
import com.oamsapps.punchd.domain.engine.Timesheet
import com.oamsapps.punchd.domain.engine.TimesheetDay
import com.oamsapps.punchd.domain.model.DayFlag
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.ui.JobDot
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TimesheetMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = state.mode == m,
                        onClick = { viewModel.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, TimesheetMode.entries.size),
                    ) { Text(stringResource(if (m == TimesheetMode.WEEK) R.string.week else R.string.month)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previous) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    if (state.mode == TimesheetMode.WEEK) "${formatDayShort(state.from)} – ${formatDayShort(state.to)}"
                    else formatMonth(state.from),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.today !in state.from..state.to) {
                    TextButton(onClick = viewModel::goToToday) { Text(stringResource(R.string.today)) }
                }
                IconButton(onClick = viewModel::next) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
                }
            }

            val ts = state.timesheet
            if (state.loading || ts == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { TotalsCard(ts) }
                    // Spec: one native ad row per 10 days of entries.
                    var daysWithEntries = 0
                    ts.days.forEach { day ->
                        item(key = day.date.toEpochDay()) {
                            DayRow(day, isToday = day.date == state.today, state = state, onClick = { openDay = day.date })
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

@Composable
private fun TotalsCard(ts: Timesheet) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalLine(stringResource(R.string.worked), formatHm(ts.worked), bold = true)
            ts.scheduled?.let { sch ->
                TotalLine(stringResource(R.string.scheduled), formatHm(sch))
                val diff = ts.worked.plus(ts.paidLeave).minus(sch)
                TotalLine(
                    stringResource(R.string.difference),
                    (if (diff.isNegative) "−" else "+") + formatHm(diff.abs()),
                )
            }
            if (ts.overtime.plus(ts.doubleTime) > Duration.ZERO) {
                TotalLine(stringResource(R.string.overtime), formatHm(ts.overtime.plus(ts.doubleTime)))
            }
            if (ts.paidLeave > Duration.ZERO) TotalLine(stringResource(R.string.paid_leave), formatHm(ts.paidLeave))
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun DayRow(day: TimesheetDay, isToday: Boolean, state: TimesheetUiState, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDayShort(day.date),
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (day.worked > Duration.ZERO || day.paidLeave > Duration.ZERO) {
                Text(formatHm(day.worked.plus(day.paidLeave)), fontWeight = FontWeight.SemiBold)
            }
        }
        day.shifts.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                state.job(row.shift.jobId)?.let { JobDot(it.color) }
                Spacer(Modifier.width(8.dp))
                val end = row.shift.end?.let { formatTime(it, row.shift.zone) } ?: stringResource(R.string.now)
                Text(
                    "${formatTime(row.shift.start, row.shift.zone)} – $end · ${formatHm(row.worked)}",
                    style = MaterialTheme.typography.bodyMedium,
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
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        DayFlags(day.flags)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
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
            modifier = Modifier.padding(top = 4.dp),
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
