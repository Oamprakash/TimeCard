package com.oam.timecard.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oam.timecard.R
import com.oam.timecard.domain.engine.PayPeriodConfig
import com.oam.timecard.domain.engine.PayPeriodType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
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

/** Pay period type, its start date and the week start. Used by onboarding, Settings and Reports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayPeriodDialog(
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
