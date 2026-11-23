package com.oamsapps.punchd.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oamsapps.punchd.R
import com.oamsapps.punchd.domain.engine.PayPeriodConfig
import com.oamsapps.punchd.domain.engine.PayPeriodType
import com.oamsapps.punchd.domain.model.PresetKind
import com.oamsapps.punchd.domain.model.WorkCountry
import com.oamsapps.punchd.ui.PayPeriodDialog
import com.oamsapps.punchd.ui.formatDateMedium
import com.oamsapps.punchd.ui.periodTypeLabel
import java.time.format.TextStyle
import java.util.Locale

/** First run: country -> rule preset, first job, pay period. Everything can be changed later in Settings. */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        LinearProgressIndicator(
            progress = { (s.step.ordinal + 1) / OnboardingStep.entries.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (s.step) {
                OnboardingStep.COUNTRY -> CountryStep(s, viewModel)
                OnboardingStep.JOB -> JobStep(s, viewModel)
                OnboardingStep.PAY_PERIOD -> PayPeriodStep(s, viewModel)
                OnboardingStep.DONE -> DoneStep()
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.step != OnboardingStep.COUNTRY) TextButton(onClick = viewModel::back) { Text(stringResource(R.string.back)) }
            Spacer(Modifier.weight(1f))
            if (s.step == OnboardingStep.DONE) {
                Button(onClick = viewModel::finish, enabled = !s.saving) { Text(stringResource(R.string.onboarding_start)) }
            } else {
                Button(onClick = viewModel::next) { Text(stringResource(R.string.next)) }
            }
        }
    }
}

@Composable
private fun CountryStep(s: OnboardingState, vm: OnboardingViewModel) {
    Text(stringResource(R.string.onboarding_welcome), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.onboarding_welcome_text))
    Text(stringResource(R.string.onboarding_where), style = MaterialTheme.typography.titleMedium)
    listOf(
        WorkCountry.US to R.string.country_us,
        WorkCountry.CALIFORNIA to R.string.country_california,
        WorkCountry.GERMANY to R.string.country_germany,
        WorkCountry.OTHER to R.string.country_other,
    ).forEach { (c, label) ->
        FilterChip(selected = s.country == c, onClick = { vm.pickCountry(c) }, label = { Text(stringResource(label)) })
    }
    Card(Modifier.fillMaxWidth()) {
        Text(
            stringResource(
                when (s.preset) {
                    PresetKind.US_FEDERAL -> R.string.preset_us_explain
                    PresetKind.CALIFORNIA -> R.string.preset_ca_explain
                    PresetKind.GERMANY -> R.string.preset_de_explain
                    PresetKind.CUSTOM -> R.string.preset_custom_explain
                }
            ),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(stringResource(R.string.pay_disclaimer), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun JobStep(s: OnboardingState, vm: OnboardingViewModel) {
    Text(stringResource(R.string.first_job_title), style = MaterialTheme.typography.headlineSmall)
    OutlinedTextField(
        s.jobName, { vm.setJob(name = it) },
        label = { Text(stringResource(R.string.job_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            s.rate, { vm.setJob(rate = it) },
            label = { Text(stringResource(R.string.hourly_rate, s.currency)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(2f),
        )
        OutlinedTextField(
            s.currency, vm::setCurrency,
            label = { Text(stringResource(R.string.currency)) }, singleLine = true, modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        s.scheduledHours, { vm.setJob(scheduled = it) },
        label = { Text(stringResource(R.string.scheduled_hours_optional)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.auto_break_title))
            Text(stringResource(R.string.auto_break_onboarding), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = s.autoBreak, onCheckedChange = vm::setAutoBreak)
    }
    if (s.jobError) Text(stringResource(R.string.invalid_job), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PayPeriodStep(s: OnboardingState, vm: OnboardingViewModel) {
    var edit by remember { mutableStateOf(false) }
    Text(stringResource(R.string.pay_period), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.onboarding_pay_text))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(periodTypeLabel(s.payType), style = MaterialTheme.typography.titleMedium)
            if (s.payType != PayPeriodType.SEMI_MONTHLY) Text(stringResource(R.string.period_starts_on, formatDateMedium(s.payAnchor)))
            Text(stringResource(R.string.week_starts_on) + ": " + s.weekStart.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        }
    }
    OutlinedButton(onClick = { edit = true }) { Text(stringResource(R.string.change)) }
    if (edit) {
        PayPeriodDialog(
            initial = PayPeriodConfig(s.payType, s.payAnchor),
            initialWeekStart = s.weekStart,
            onDismiss = { edit = false },
            onSave = { c, ws -> vm.setPayPeriod(c, ws); edit = false },
        )
    }
}

@Composable
private fun DoneStep() {
    Text(stringResource(R.string.onboarding_done_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.onboarding_done_text))
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.pay_disclaimer), style = MaterialTheme.typography.bodySmall)
}
