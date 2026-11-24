package com.oamsapps.punchd.ui.job

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.oamsapps.punchd.ui.theme.selectorContainerColor
import com.oamsapps.punchd.R
import com.oamsapps.punchd.ui.FormSkeleton
import com.oamsapps.punchd.domain.model.PresetKind
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobEditScreen(onClose: () -> Unit, viewModel: JobEditViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.done.collect { onClose() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (s.isNew) R.string.add_job else R.string.edit_job)) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                // Destructive action out of the thumb zone, away from Save (same as Edit shift).
                actions = {
                    if (!s.isNew && !s.loading) IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete_job), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
        // Actions pinned to the bottom so Save is always reachable; errors show right above them.
        bottomBar = {
            if (!s.loading) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        s.error?.let {
                            Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = !s.saving && !s.limitReached,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
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
            if (s.limitReached) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(stringResource(R.string.job_limit), color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
            }
            Field(s.name, { v -> viewModel.edit { copy(name = v) } }, stringResource(R.string.job_name), numeric = false)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                RateField(s.rate, s.currency, { v -> viewModel.edit { copy(rate = v) } }, Modifier.weight(1.8f))
                CurrencyField(s.currency, { c -> viewModel.edit { copy(currency = c) } }, Modifier.weight(1f))
            }
            Field(s.scheduledHours, { v -> viewModel.edit { copy(scheduledHours = v) } }, stringResource(R.string.scheduled_hours_optional))

            Text(stringResource(R.string.color), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
                JOB_COLORS.forEach { c -> ColorSwatch(c, selected = c == s.color) { viewModel.edit { copy(color = c) } } }
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.rules), style = MaterialTheme.typography.titleSmall)
            // 2×2 preset grid: equal widths, label centred, ✓ + mint on the selected one.
            listOf(
                PresetKind.US_FEDERAL to R.string.preset_short_us, PresetKind.CALIFORNIA to R.string.preset_short_ca,
                PresetKind.GERMANY to R.string.preset_short_de, PresetKind.CUSTOM to R.string.preset_short_custom,
            ).chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (kind, label) ->
                        val selected = s.preset == kind
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.applyPreset(kind) },
                            label = { Text(stringResource(label), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = selectorContainerColor()),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        )
                    }
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
            Text(stringResource(R.string.rules_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.preset == PresetKind.GERMANY) {
                Text(stringResource(R.string.rules_de_breaks_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!s.isNew) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.archive_job))
                        Text(stringResource(R.string.archive_job_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = s.archived, onCheckedChange = viewModel::setArchived)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_job_q, s.name.trim())) },
            text = { Text(stringResource(R.string.delete_job_warning)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** 36dp colour circle; the selected one gets a ✓ and an outer ring. */
@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape).background(Color(color))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
        JobFormError.DELETE_RUNNING -> R.string.err_delete_running
        JobFormError.SAVE_FAILED -> R.string.save_failed_try_again
    }
)
