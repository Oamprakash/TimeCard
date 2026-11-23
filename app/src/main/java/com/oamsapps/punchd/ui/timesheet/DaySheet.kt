package com.oamsapps.punchd.ui.timesheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.oamsapps.punchd.R
import com.oamsapps.punchd.domain.engine.TimesheetDay
import com.oamsapps.punchd.domain.model.DayMarkType
import com.oamsapps.punchd.ui.JobDot
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.formatDateMedium
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.formatTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/** Day detail: shifts (tap to edit), add shift, and sick / holiday / paid-leave per job. */
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(formatDateMedium(day.date), style = MaterialTheme.typography.titleLarge)

            if (day.shifts.isEmpty()) {
                Text(stringResource(R.string.no_shifts), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            day.shifts.forEach { row ->
                val job = jobs.firstOrNull { it.id == row.shift.jobId }
                Row(
                    Modifier.fillMaxWidth().clickable { onEditShift(row.shift.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    job?.let { JobDot(it.color, 12.dp) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        val end = row.shift.end?.let { formatTime(it, row.shift.zone) } ?: stringResource(R.string.now)
                        Text("${formatTime(row.shift.start, row.shift.zone)} – $end", fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                job?.name,
                                if (row.breaks > Duration.ZERO) stringResource(R.string.break_short, formatHm(row.breaks)) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(formatHm(row.worked))
                }
            }
            FilledTonalButton(onClick = onAddShift, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_shift))
            }

            HorizontalDivider()
            Text(stringResource(R.string.day_off_title), style = MaterialTheme.typography.titleMedium)
            jobs.forEach { job ->
                MarkEditor(
                    job = job,
                    current = day.marks.firstOrNull { it.jobId == job.id }?.let { it.type to it.paidTime },
                    onSet = { type, paid -> onSetMark(job.id, type, paid) },
                )
            }
            Spacer(Modifier.padding(bottom = 24.dp))
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JobDot(job.color)
            Spacer(Modifier.width(8.dp))
            Text(job.name, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == null, onClick = { type = null }, label = { Text(stringResource(R.string.mark_none)) })
            DayMarkType.entries.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(markLabel(t)) })
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
            TextButton(
                onClick = { onSet(type, parsed?.let(::toDuration) ?: Duration.ZERO) },
                enabled = type == null || parsed != null,
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

private fun toDuration(hours: BigDecimal): Duration =
    Duration.ofMinutes(hours.multiply(BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).toLong())

private fun toHoursText(d: Duration): String =
    BigDecimal(d.toMinutes()).divide(BigDecimal(60), 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
