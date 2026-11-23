package com.oamsapps.punchd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * One brand palette: emerald for the main action, soft mint for selected states (chips, nav pill,
 * progress track), so nothing falls back to Material's default lavender. Dynamic colour is off on
 * purpose: the clock button must look the same on every phone.
 */
object Brand {
    val Emerald = Color(0xFF1B873F)
    val EmeraldDark = Color(0xFF6FD28E)
    /** Clock out / overtime: a red that sits well next to the emerald. */
    val ClockOut = Color(0xFFC62828)
    val ClockOutDark = Color(0xFFFF8A80)
}

private val Light = lightColorScheme(
    primary = Brand.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9EFD5),
    onPrimaryContainer = Color(0xFF062F15),
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5ECDB), // selected job chip, nav bar pill
    onSecondaryContainer = Color(0xFF0E2A17),
    tertiary = Color(0xFF2E6A6F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6ECEF),
    onTertiaryContainer = Color(0xFF002023),
    error = Brand.ClockOut,
    onError = Color.White,
    background = Color(0xFFF7FAF6),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF7FAF6),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFDDE5DC), // progress track, skeletons
    onSurfaceVariant = Color(0xFF59625A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F0),
    surfaceContainer = Color(0xFFEBF0EA), // bottom nav background
    surfaceContainerHigh = Color(0xFFE6ECE5),
    surfaceContainerHighest = Color(0xFFE0E6DF), // cards
    outline = Color(0xFF8A938A),
    outlineVariant = Color(0xFFC6CEC5),
)

private val Dark = darkColorScheme(
    primary = Brand.EmeraldDark,
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF0E5229),
    onPrimaryContainer = Color(0xFFC9EFD5),
    secondary = Color(0xFFB4CCB9),
    onSecondary = Color(0xFF203527),
    secondaryContainer = Color(0xFF364B3C),
    onSecondaryContainer = Color(0xFFD5ECDB),
    tertiary = Color(0xFFA0CFD3),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF124E53),
    onTertiaryContainer = Color(0xFFC6ECEF),
    error = Brand.ClockOutDark,
    onError = Color(0xFF690005),
    background = Color(0xFF101410),
    onBackground = Color(0xFFDFE4DD),
    surface = Color(0xFF101410),
    onSurface = Color(0xFFDFE4DD),
    surfaceVariant = Color(0xFF3F4940),
    onSurfaceVariant = Color(0xFFBEC9BE),
    surfaceContainerLowest = Color(0xFF0B0F0B),
    surfaceContainerLow = Color(0xFF181D18),
    surfaceContainer = Color(0xFF1C211C),
    surfaceContainerHigh = Color(0xFF262B26),
    surfaceContainerHighest = Color(0xFF313631),
    outline = Color(0xFF889388),
    outlineVariant = Color(0xFF3F4940),
)

@Composable
fun PunchdTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}

/** Colour of the big button: emerald to clock in, red to clock out. */
@Composable
fun clockInColor(): Color = MaterialTheme.colorScheme.primary

@Composable
fun clockOutColor(): Color = MaterialTheme.colorScheme.error
