package com.oam.timecard.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oam.timecard.R
import com.oam.timecard.ui.findActivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.billing_unavailable)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.remove_ads), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.remove_ads_benefits), style = MaterialTheme.typography.bodyMedium)
                    when {
                        s.adsRemoved -> Text(stringResource(R.string.remove_ads_owned), color = MaterialTheme.colorScheme.primary)
                        s.purchasePending -> Text(stringResource(R.string.purchase_pending))
                        else -> Button(
                            onClick = {
                                val activity = context.findActivity()
                                if (activity == null || !viewModel.buyRemoveAds(activity)) {
                                    scope.launch { snackbar.showSnackbar(unavailable) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(s.price?.let { stringResource(R.string.remove_ads_for, it) } ?: stringResource(R.string.remove_ads))
                        }
                    }
                    TextButton(onClick = viewModel::restore) { Text(stringResource(R.string.restore_purchases)) }
                }
            }
            if (s.privacyOptionsRequired) {
                OutlinedButton(
                    onClick = { context.findActivity()?.let(viewModel::privacyOptions) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.privacy_options)) }
            }
            Text(
                stringResource(R.string.settings_more_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
