package app.tfl.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.tfl.feature.settings.SettingsScreen
import app.tfl.feature.settings.network.NetworkSettingsScreen
import app.tfl.feature.settings.security.SecuritySettingsScreen
import kotlinx.serialization.Serializable

/** The Settings tab: the settings list and the screens it opens. */
@Serializable
data object SettingsGraph

@Serializable
data object SettingsRoute

@Serializable
data object SecuritySettingsRoute

@Serializable
data object NetworkSettingsRoute

fun NavController.navigateToSecuritySettings() = navigate(SecuritySettingsRoute)

fun NavController.navigateToNetworkSettings() = navigate(NetworkSettingsRoute)

/**
 * @param developerSection extra rows at the end of the list; supplied only by debug builds.
 */
fun NavGraphBuilder.settingsGraph(
    appVersion: String,
    onOpenSecurity: () -> Unit,
    onOpenNetwork: () -> Unit,
    onBack: () -> Unit,
    onNotYetAvailable: () -> Unit,
    developerSection: (@Composable () -> Unit)? = null,
) {
    navigation<SettingsGraph>(startDestination = SettingsRoute) {
        composable<SettingsRoute> {
            SettingsScreen(
                appVersion = appVersion,
                onOpenSecurity = onOpenSecurity,
                onOpenNetwork = onOpenNetwork,
                onNotYetAvailable = onNotYetAvailable,
                developerSection = developerSection,
            )
        }
        composable<SecuritySettingsRoute> {
            SecuritySettingsScreen(onBack = onBack, onNotYetAvailable = onNotYetAvailable)
        }
        composable<NetworkSettingsRoute> {
            NetworkSettingsScreen(onBack = onBack, onNotYetAvailable = onNotYetAvailable)
        }
    }
}
