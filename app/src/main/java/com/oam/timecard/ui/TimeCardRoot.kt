package com.oam.timecard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.oam.timecard.R
import com.oam.timecard.ui.clock.ClockScreen
import com.oam.timecard.ui.reports.ReportsScreen
import com.oam.timecard.ui.shift.ShiftEditScreen
import com.oam.timecard.ui.shift.ShiftEditViewModel
import com.oam.timecard.ui.settings.SettingsScreen
import com.oam.timecard.ui.timesheet.TimesheetScreen

/** Navigation shell. */
private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Clock("clock", R.string.tab_clock, Icons.Filled.PlayArrow),
    Timesheet("timesheet", R.string.tab_timesheet, Icons.Filled.DateRange),
    Reports("reports", R.string.tab_reports, Icons.Filled.Info),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private const val SHIFT_ROUTE = "shift"

@Composable
fun TimeCardRoot() {
    MaterialTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route
        val onTab = current == null || Tab.entries.any { it.route == current }
        Scaffold(
            bottomBar = {
                if (onTab) NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Tab.Clock.route, modifier = Modifier.padding(padding)) {
                composable(Tab.Clock.route) { ClockScreen() }
                composable(Tab.Timesheet.route) {
                    TimesheetScreen(
                        onAddShift = { date -> nav.navigate("$SHIFT_ROUTE?${ShiftEditViewModel.ARG_DATE}=$date") },
                        onEditShift = { id -> nav.navigate("$SHIFT_ROUTE?${ShiftEditViewModel.ARG_ID}=$id") },
                    )
                }
                composable(Tab.Reports.route) { ReportsScreen() }
                composable(Tab.Settings.route) { SettingsScreen() }
                composable(
                    route = "$SHIFT_ROUTE?${ShiftEditViewModel.ARG_ID}={${ShiftEditViewModel.ARG_ID}}" +
                        "&${ShiftEditViewModel.ARG_DATE}={${ShiftEditViewModel.ARG_DATE}}",
                    arguments = listOf(
                        navArgument(ShiftEditViewModel.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                        navArgument(ShiftEditViewModel.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) {
                    ShiftEditScreen(onClose = { nav.popBackStack() })
                }
            }
        }
    }
}
