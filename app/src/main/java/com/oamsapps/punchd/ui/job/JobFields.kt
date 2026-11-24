package com.oamsapps.punchd.ui.job

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.oamsapps.punchd.R
import com.oamsapps.punchd.util.currencySymbol

/*
 * Job fields shared by Edit job and onboarding, so a job is entered the same way everywhere
 * (the Clock tab opens Edit job instead of having its own form).
 */

/** Common currencies first; any other valid code the job already uses is added to the list. */
private val CURRENCIES = listOf("USD", "EUR", "GBP", "INR", "CAD", "AUD", "NZD", "CHF", "JPY", "SGD", "AED", "ZAR", "MXN", "BRL", "SEK", "NOK", "DKK", "PLN")

/** "Hourly rate" with the currency symbol as a prefix ("$ 50"). */
@Composable
fun RateField(value: String, currency: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.hourly_rate_plain)) },
        prefix = { Text(currencySymbol(currency) + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Currency as an exposed dropdown ("USD ▾"); names in the menu, code in the field. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val options = remember(value) { if (value in CURRENCIES || value.isBlank()) CURRENCIES else listOf(value) + CURRENCIES }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.currency)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { code ->
                val name = remember(code) { runCatching { java.util.Currency.getInstance(code).displayName }.getOrDefault(code) }
                DropdownMenuItem(
                    text = { Text("$code · $name", maxLines = 1) },
                    onClick = { onChange(code); open = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

