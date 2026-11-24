package com.oamsapps.punchd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oamsapps.punchd.R
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.domain.engine.PayPeriodType
import com.oamsapps.punchd.ui.theme.selectorContainerColor
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun periodTypeLabel(type: PayPeriodType) = stringResource(
    when (type) {
        PayPeriodType.WEEKLY -> R.string.period_weekly
        PayPeriodType.BIWEEKLY -> R.string.period_biweekly
        PayPeriodType.SEMI_MONTHLY -> R.string.period_semi_monthly
        PayPeriodType.MONTHLY -> R.string.period_monthly
    }
)

/** "Sun, 22 Nov 2026" / "So., 22. Nov. 2026", in the locale's own order. */
private fun formatDateWithWeekday(date: LocalDate): String {
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyy")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/**
 * Pay period frequency, its start date and the week start, as a modal bottom sheet:
 * radio cards for the frequency, a date field, a segmented week-start control, filled Save.
 * Used by onboarding, Settings and Reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayPeriodDialog(
    initial: PayPeriodConfig,
    initialWeekStart: DayOfWeek,
    onDismiss: () -> Unit,
    onSave: (PayPeriodConfig, DayOfWeek) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide the sheet down first, then report back, so Cancel/Save animate like a drag-to-dismiss.
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        PayPeriodSheetContent(
            initial = initial,
            initialWeekStart = initialWeekStart,
            onCancel = { close(onDismiss) },
            onSave = { config, weekStart -> close { onSave(config, weekStart) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayPeriodSheetContent(
    initial: PayPeriodConfig,
    initialWeekStart: DayOfWeek,
    onCancel: () -> Unit,
    onSave: (PayPeriodConfig, DayOfWeek) -> Unit,
) {
    var type by remember { mutableStateOf(initial.type) }
    var anchor by remember { mutableStateOf(initial.anchor) }
    var weekStart by remember { mutableStateOf(initialWeekStart) }
    var pickDate by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.pay_period), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.select_frequency), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PayPeriodType.entries.forEach { t ->
                FrequencyOption(
                    label = periodTypeLabel(t),
                    detail = if (t == PayPeriodType.SEMI_MONTHLY) stringResource(R.string.period_semi_monthly_detail) else null,
                    selected = type == t,
                    onClick = { type = t },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (type != PayPeriodType.SEMI_MONTHLY) {
            // Read-only field; an overlay takes the tap so the whole field opens the date picker.
            Box {
                OutlinedTextField(
                    value = stringResource(R.string.period_starts_on, formatDateWithWeekday(anchor)),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.period_start_date)) },
                    trailingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier.matchParentSize().padding(top = 8.dp)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.period_start_date)) { pickDate = true }
                )
            }
        } else {
            Text(stringResource(R.string.semi_monthly_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.week_starts_on), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                SegmentedButton(
                    selected = weekStart == d,
                    onClick = { weekStart = d },
                    shape = SegmentedButtonDefaults.itemShape(i, days.size, RoundedCornerShape(8.dp)),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    icon = {},
                    label = { Text(d.getDisplayName(TextStyle.FULL, Locale.getDefault()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(PayPeriodConfig(type, anchor), weekStart) }) { Text(stringResource(R.string.save)) }
        }
    }

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

/** One frequency choice: outlined card with a trailing radio; selected = mint fill + brand border. */
@Composable
private fun FrequencyOption(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectorContainerColor() else cs.surface,
        border = BorderStroke(1.dp, if (selected) cs.primary else cs.outline),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(name = "Pay period sheet", showBackground = true, heightDp = 800)
@Composable
private fun PayPeriodDialogPreview() = com.oamsapps.punchd.ui.theme.PunchdTheme(dark = false) {
    Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            PayPeriodSheetContent(PayPeriodConfig(PayPeriodType.BIWEEKLY, LocalDate.of(2026, 11, 22)), DayOfWeek.SUNDAY, {}, { _, _ -> })
        }
    }
}
