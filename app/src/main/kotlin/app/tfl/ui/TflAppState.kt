package app.tfl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.tfl.navigation.TopLevelDestination

@Composable
fun rememberTflAppState(navController: NavHostController = rememberNavController()): TflAppState =
    remember(navController) { TflAppState(navController) }

/** Navigation state shared by the app shell: current destination, tab selection, bottom bar visibility. */
@Stable
class TflAppState(val navController: NavHostController) {

    private val currentDestination: NavDestination?
        @Composable get() {
            val entry by navController.currentBackStackEntryAsState()
            return entry?.destination
        }

    /** True on a tab's first screen; detail screens (Settings → Security…) hide the bar. */
    val showBottomBar: Boolean
        @Composable get() {
            val destination = currentDestination ?: return true
            return TopLevelDestination.entries.any { destination.hasRoute(it.startRoute) }
        }

    @Composable
    fun isSelected(destination: TopLevelDestination): Boolean =
        currentDestination?.hierarchy?.any { it.hasRoute(destination.baseRoute) } == true

    /** Switches tabs, keeping each tab's back stack so returning to it restores where you were. */
    fun navigateTo(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
