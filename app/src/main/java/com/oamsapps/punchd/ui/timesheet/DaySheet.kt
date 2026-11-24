package com.oamsapps.punchd.ui.timesheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.draw.clip
import com.oamsapps.punchd.ui.formatDateWithWeekday
import com.oamsapps.punchd.R
import com.oamsapps.punchd.domain.engine.TimesheetDay
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.ui.JobDot
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.formatTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Day detail as a bottom sheet: date + day total, the day's shifts (tap to edit), a filled
 * "+ Add shift", then one outlined card per job with a 2×2 leave selector (None / Sick / Holiday / Paid leave).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    day: TimesheetDay,
    jobs: List<JobLabel>,
    onDismiss: () -> Unit,
    onAddShift: () -> Unit,
    onEditShift: (Long) -> Unit,
    onSetMark: (jobId: Long, type: DayMarkType?, paid: Duration) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            // Header
            Text(formatDateWithWeekday(day.date), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            val worked = day.shifts.fold(Duration.ZERO) { acc, r -> acc.plus(r.worked) }
            Text(
                if (day.shifts.isEmpty()) stringResource(R.string.no_shifts) else stringResource(R.string.day_worked, formatHm(worked)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Shifts
            if (day.shifts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                day.shifts.forEach { row ->
                    val job = jobs.firstOrNull { it.id == row.shift.jobId }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .clickable { onEditShift(row.shift.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        job?.let { JobDot(it.color, 12.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val end = row.shift.end?.let { formatTime(it, row.shift.zone) } ?: stringResource(R.string.now)
                            Text("${formatTime(row.shift.start, row.shift.zone)} – $end", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    job?.name,
                                    if (row.breaks > Duration.ZERO) stringResource(R.string.break_short, formatHm(row.breaks)) else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatHm(row.worked), style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Primary action
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAddShift, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_shift))
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            // Time off / leave, one card per job
            Text(stringResource(R.string.day_off_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                jobs.forEach { job ->
                    MarkEditor(
                        job = job,
                        current = day.marks.firstOrNull { it.jobId == job.id }?.let { it.type to it.paidTime },
                        onSet = { type, paid -> onSetMark(job.id, type, paid) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkEditor(
    job: JobLabel,
    current: Pair<DayMarkType, Duration>?,
    onSet: (DayMarkType?, Duration) -> Unit,
) {
    // Default paid hours: a fifth of the weekly schedule, else 8 h.
    val defaultPaid = job.scheduledPerWeek?.dividedBy(5) ?: Duration.ofHours(8)
    var type by remember(current) { mutableStateOf(current?.first) }
    var hoursText by remember(current) { mutableStateOf(toHoursText(current?.second ?: defaultPaid)) }
    val parsed = hoursText.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() >= 0 && it <= BigDecimal(24) }

    OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JobDot(job.color)
                Spacer(Modifier.width(8.dp))
                Text(job.name, style = MaterialTheme.typography.titleSmall)
            }
            // 2×2 grid: equal widths, 48dp tall, never clips at large font scales (labels wrap instead).
            val options: List<DayMarkType?> = listOf(null) + DayMarkType.entries
            options.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { t ->
                        val selected = type == t
                        FilterChip(
                            selected = selected,
                            onClick = { type = t },
                            label = { Text(if (t == null) stringResource(R.string.mark_none) else markLabel(t)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (type != null) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = { Text(stringResource(R.string.paid_hours)) },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val changed = type != current?.first || (type != null && parsed != null && toDuration(parsed) != current?.second)
            if (changed) {
                Button(
                    onClick = { onSet(type, parsed?.let(::toDuration) ?: Duration.ZERO) },
                    enabled = type == null || parsed != null,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

private fun toDuration(hours: BigDecimal): Duration =
    Duration.ofMinutes(hours.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toLong())

private fun toHoursText(d: Duration): String =
    BigDecimal(d.toMinutes()).divide(BigDecimal(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
