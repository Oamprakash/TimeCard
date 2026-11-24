package com.oamsapps.punchd.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.oamsapps.punchd.R
import com.oamsapps.punchd.ui.clock.ClockScreen
import com.oamsapps.punchd.ui.theme.PunchdTheme
import com.oamsapps.punchd.ui.job.JobEditScreen
import com.oamsapps.punchd.ui.job.JobEditViewModel
import com.oamsapps.punchd.ui.onboarding.OnboardingScreen
import com.oamsapps.punchd.ui.reports.ReportsScreen
import com.oamsapps.punchd.ui.shift.ShiftEditScreen
import com.oamsapps.punchd.ui.shift.ShiftEditViewModel
import com.oamsapps.punchd.ui.settings.SettingsScreen
import com.oamsapps.punchd.ui.timesheet.TimesheetScreen

/** Navigation shell. */
private enum class Tab(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
) {
    Clock("clock", R.string.tab_clock, Icons.Filled.PlayArrow),
    Timesheet("timesheet", R.string.tab_timesheet, Icons.Filled.DateRange),
    // Bar chart (not in the core icon set, so it's a vector drawable); "i" would read as Help/About.
    Reports("reports", R.string.tab_reports, iconRes = R.drawable.ic_nav_reports),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

private const val SHIFT_ROUTE = "shift"
private const val JOB_ROUTE = "job"

@Composable
fun TimeCardRoot(rootViewModel: RootViewModel = hiltViewModel()) {
    val root by rootViewModel.state.collectAsStateWithLifecycle()
    PunchdTheme {
        when (root) {
            RootState.LOADING -> Unit // first frame only; the clock state shows within the 1 s budget
            RootState.ONBOARDING -> OnboardingScreen()
            RootState.MAIN -> MainTabs()
        }
    }
}

@Composable
private fun MainTabs() {
    run {
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
                            icon = { Icon(tab.icon ?: ImageVector.vectorResource(tab.iconRes!!), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(nav, startDestination = Tab.Clock.route, modifier = Modifier.padding(padding)) {
                composable(Tab.Clock.route) { ClockScreen(onAddJob = { nav.navigate("$JOB_ROUTE?${JobEditViewModel.ARG_SELECT}=true") }) }
                composable(Tab.Timesheet.route) {
                    TimesheetScreen(
                        onAddShift = { date -> nav.navigate("$SHIFT_ROUTE?${ShiftEditViewModel.ARG_DATE}=$date") },
                        onEditShift = { id -> nav.navigate("$SHIFT_ROUTE?${ShiftEditViewModel.ARG_ID}=$id") },
                    )
                }
                composable(Tab.Reports.route) { ReportsScreen() }
                composable(Tab.Settings.route) {
                    SettingsScreen(
                        onEditJob = { id -> nav.navigate("$JOB_ROUTE?${JobEditViewModel.ARG_ID}=$id") },
                        onAddJob = { nav.navigate(JOB_ROUTE) },
                    )
                }
                composable(
                    route = "$JOB_ROUTE?${JobEditViewModel.ARG_ID}={${JobEditViewModel.ARG_ID}}" +
                        "&${JobEditViewModel.ARG_SELECT}={${JobEditViewModel.ARG_SELECT}}",
                    arguments = listOf(
                        navArgument(JobEditViewModel.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                        navArgument(JobEditViewModel.ARG_SELECT) { type = NavType.BoolType; defaultValue = false },
                    ),
                ) {
                    JobEditScreen(onClose = { nav.popBackStack() })
                }
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
