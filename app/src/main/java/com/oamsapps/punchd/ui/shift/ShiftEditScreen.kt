package com.oamsapps.punchd.ui.shift

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import com.oamsapps.punchd.ui.JobLabel
import com.oamsapps.punchd.ui.formatDateWithWeekday
import com.oamsapps.punchd.ui.theme.selectorContainerColor
import java.time.Duration
import com.oamsapps.punchd.R
import com.oamsapps.punchd.domain.engine.ShiftError
import com.oamsapps.punchd.ui.JobDot
import com.oamsapps.punchd.ui.formatHm
import com.oamsapps.punchd.ui.FormSkeleton
import com.oamsapps.punchd.ui.findActivity
import com.oamsapps.punchd.ui.formatTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditScreen(onClose: () -> Unit, viewModel: ShiftEditViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    // Which time is being picked: "start", "end", "b<i>s", "b<i>e".
    var timeTarget by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.done.collect { saved ->
            if (saved) context.findActivity()?.let { viewModel.showInterstitialAfterSave(it) }
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.add_shift else R.string.edit_shift)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (!s.isNew && !s.loading) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        // Save pinned (always reachable above the keyboard); errors right above it.
        bottomBar = {
            if (!s.loading) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(
                        Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (s.saveFailed) {
                            Text(stringResource(R.string.save_failed_try_again), color = MaterialTheme.colorScheme.error)
                        }
                        s.errors.forEach { e ->
                            Text(errorText(e), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (s.saveFailed || s.errors.isNotEmpty()) Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = viewModel::save,
                            enabled = !s.saving && s.jobId != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        },
    ) { padding ->
        if (s.loading) {
            FormSkeleton(fields = 5)
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (s.jobs.size > 1) JobDropdown(s.jobs, s.jobId, viewModel::setJob)

            FieldButton(stringResource(R.string.date), formatDateWithWeekday(s.date), enabled = !s.isRunning) { datePicker = true }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FieldButton(stringResource(R.string.start), formatTime(s.start), Modifier.weight(1f)) { timeTarget = "start" }
                if (s.isRunning) {
                    FieldButton(stringResource(R.string.end), stringResource(R.string.now), Modifier.weight(1f), enabled = false) {}
                } else {
                    FieldButton(stringResource(R.string.end), formatTime(s.end), Modifier.weight(1f)) { timeTarget = "end" }
                }
            }
            viewModel.preview(s)?.let { p ->
                WorkedSummary(p)
            }
            if (s.endsNextDay) {
                Text(stringResource(R.string.ends_next_day), style = MaterialTheme.typography.bodySmall)
            }
            if (s.isRunning) {
                Text(stringResource(R.string.running_shift_hint), style = MaterialTheme.typography.bodySmall)
            }

            // Breaks
            Text(stringResource(R.string.breaks), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.breaks.forEachIndexed { i, b ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldButton(stringResource(R.string.start), formatTime(b.start), Modifier.weight(1f), enabled = !s.isRunning) { timeTarget = "b${i}s" }
                    FieldButton(stringResource(R.string.end), formatTime(b.end), Modifier.weight(1f), enabled = !s.isRunning) { timeTarget = "b${i}e" }
                    if (!s.isRunning) IconButton(onClick = { viewModel.removeBreak(i) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.remove_break))
                    }
                }
            }
            s.runningBreakSince?.let {
                Text(stringResource(R.string.break_running_since, formatTime(it)), style = MaterialTheme.typography.bodySmall)
            }
            if (!s.isRunning) {
                OutlinedButton(onClick = viewModel::addBreak) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_break))
                }
            }

            OutlinedTextField(
                value = s.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )

        }
    }

    if (datePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = s.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    datePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(pickerState) }
    }

    timeTarget?.let { target ->
        val breakIndex = target.drop(1).dropLast(1).toIntOrNull()
        val initial = when {
            target == "start" -> s.start
            target == "end" -> s.end
            breakIndex != null && target.endsWith("s") -> s.breaks[breakIndex].start
            breakIndex != null -> s.breaks[breakIndex].end
            else -> LocalTime.NOON
        }
        TimePickerDialog(
            initial = initial,
            onDismiss = { timeTarget = null },
            onConfirm = { t ->
                when {
                    target == "start" -> viewModel.setStart(t)
                    target == "end" -> viewModel.setEnd(t)
                    breakIndex != null && target.endsWith("s") ->
                        viewModel.setBreak(breakIndex, s.breaks[breakIndex].copy(start = t))
                    breakIndex != null -> viewModel.setBreak(breakIndex, s.breaks[breakIndex].copy(end = t))
                }
                timeTarget = null
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_shift_q)) },
            text = { if (s.isRunning) Text(stringResource(R.string.delete_running_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Job picker for shifts: "● Primary job ▾" (scales to any number of jobs, unlike a chip row). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobDropdown(jobs: List<JobLabel>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = jobs.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.job)) },
            leadingIcon = selected?.let { j -> { JobDot(j.color, 12.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            jobs.forEach { j ->
                DropdownMenuItem(
                    text = { Text(j.name) },
                    leadingIcon = { JobDot(j.color, 12.dp) },
                    onClick = { onSelect(j.id); open = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** "7:30 worked · 30 min break", tinted, right under the times so the result is visible while editing. */
@Composable
private fun WorkedSummary(p: WorkedPreview) {
    Surface(color = selectorContainerColor(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.day_worked, formatHm(p.worked)),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (p.breaks > Duration.ZERO) {
                Text(stringResource(R.string.break_short, formatHm(p.breaks)), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FieldButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}

@Composable
private fun errorText(e: ShiftError): String = stringResource(
    when (e) {
        ShiftError.EMPTY -> R.string.err_empty
        ShiftError.START_IN_FUTURE -> R.string.err_start_future
        ShiftError.END_IN_FUTURE -> R.string.err_end_future
        ShiftError.BREAK_EMPTY -> R.string.err_break_empty
        ShiftError.BREAK_OUTSIDE_SHIFT -> R.string.err_break_outside
        ShiftError.BREAKS_OVERLAP -> R.string.err_breaks_overlap
        ShiftError.BREAKS_LONGER_THAN_SHIFT -> R.string.err_breaks_longer
        ShiftError.OVERLAPS_OTHER_SHIFT -> R.string.err_overlap
    }
)
