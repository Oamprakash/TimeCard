package com.oamsapps.punchd.ui.job

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.punchd.R
import com.oamsapps.punchd.ui.FormSkeleton
import com.oamsapps.punchd.domain.model.PresetKind
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobEditScreen(onClose: () -> Unit, viewModel: JobEditViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.done.collect { onClose() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.add_job else R.string.edit_job)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (s.loading) {
            FormSkeleton(fields = 7)
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Field(s.name, { v -> viewModel.edit { copy(name = v) } }, stringResource(R.string.job_name), numeric = false)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(s.rate, { v -> viewModel.edit { copy(rate = v) } }, stringResource(R.string.hourly_rate, s.currency), Modifier.weight(2f))
                Field(s.currency, { v -> viewModel.edit { copy(currency = v.uppercase().take(3)) } }, stringResource(R.string.currency), Modifier.weight(1f), numeric = false)
            }
            Field(s.scheduledHours, { v -> viewModel.edit { copy(scheduledHours = v) } }, stringResource(R.string.scheduled_hours_optional))

            Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                JOB_COLORS.forEach { c ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                            .then(if (c == s.color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { viewModel.edit { copy(color = c) } },
                    )
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.rules), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PresetKind.US_FEDERAL to R.string.preset_short_us, PresetKind.CALIFORNIA to R.string.preset_short_ca,
                    PresetKind.GERMANY to R.string.preset_short_de, PresetKind.CUSTOM to R.string.preset_short_custom,
                ).forEach { (kind, label) ->
                    FilterChip(selected = s.preset == kind, onClick = { viewModel.applyPreset(kind) }, label = { Text(stringResource(label)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(s.dailyOtHours, { v -> viewModel.edit { copy(dailyOtHours = v) } }, stringResource(R.string.rule_daily_ot), Modifier.weight(1f))
                Field(s.dailyDoubleHours, { v -> viewModel.edit { copy(dailyDoubleHours = v) } }, stringResource(R.string.rule_daily_double), Modifier.weight(1f))
            }
            Field(s.weeklyOtHours, { v -> viewModel.edit { copy(weeklyOtHours = v) } }, stringResource(R.string.rule_weekly_ot))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(s.otMultiplier, { v -> viewModel.edit { copy(otMultiplier = v) } }, stringResource(R.string.rule_ot_multiplier), Modifier.weight(1f))
                Field(s.doubleMultiplier, { v -> viewModel.edit { copy(doubleMultiplier = v) } }, stringResource(R.string.rule_double_multiplier), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(s.autoBreakMinutes, { v -> viewModel.edit { copy(autoBreakMinutes = v) } }, stringResource(R.string.rule_auto_break_min), Modifier.weight(1f))
                Field(s.autoBreakAfterHours, { v -> viewModel.edit { copy(autoBreakAfterHours = v) } }, stringResource(R.string.rule_auto_break_after), Modifier.weight(1f))
            }
            Field(s.maxDailyHours, { v -> viewModel.edit { copy(maxDailyHours = v) } }, stringResource(R.string.rule_max_daily))
            Text(stringResource(R.string.rules_hint), style = MaterialTheme.typography.bodySmall)
            if (s.preset == PresetKind.GERMANY) {
                Text(stringResource(R.string.rules_de_breaks_hint), style = MaterialTheme.typography.bodySmall)
            }

            if (!s.isNew) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.archive_job))
                        Text(stringResource(R.string.archive_job_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = s.archived, onCheckedChange = viewModel::setArchived)
                }
            }

            s.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::save, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), numeric: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier,
    )
}

@Composable
private fun errorText(e: JobFormError) = stringResource(
    when (e) {
        JobFormError.NAME -> R.string.err_job_name
        JobFormError.RATE -> R.string.err_job_rate
        JobFormError.CURRENCY -> R.string.err_job_currency
        JobFormError.HOURS -> R.string.err_job_hours
        JobFormError.MULTIPLIER -> R.string.err_job_multiplier
        JobFormError.DOUBLE_BEFORE_OT -> R.string.err_job_double_before_ot
        JobFormError.JOB_LIMIT -> R.string.job_limit
        JobFormError.ARCHIVE_RUNNING -> R.string.err_archive_running
        JobFormError.SAVE_FAILED -> R.string.save_failed_try_again
    }
)
