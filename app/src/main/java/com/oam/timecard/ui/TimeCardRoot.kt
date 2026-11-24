package com.oam.timecard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oam.timecard.R
import com.oam.timecard.ui.clock.ClockScreen

/** Navigation shell. Timesheet and Reports arrive in Sprint 3, Settings in Sprint 5. */
private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Clock("clock", R.string.tab_clock, Icons.Filled.PlayArrow),
    Timesheet("timesheet", R.string.tab_timesheet, Icons.Filled.DateRange),
    Reports("reports", R.string.tab_reports, Icons.Filled.Info),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun TimeCardRoot() {
    MaterialTheme {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route
        Scaffold(
            bottomBar = {
                NavigationBar {
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
                Tab.entries.filter { it != Tab.Clock }.forEach { tab ->
                    composable(tab.route) { Placeholder(stringResource(tab.label)) }
                }
            }
        }
    }
}

@Composable
private fun Placeholder(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title) }
}
