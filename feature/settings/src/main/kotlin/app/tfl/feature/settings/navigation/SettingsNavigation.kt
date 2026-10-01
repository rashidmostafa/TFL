package app.tfl.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.tfl.feature.settings.SettingsScreen
import app.tfl.feature.settings.nearby.NearbySetupScreen
import app.tfl.feature.settings.network.NetworkSettingsScreen
import app.tfl.feature.settings.notifications.NotificationSettingsScreen
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

@Serializable
data object NotificationSettingsRoute

/** Also opened from the chats list, when Nearby is on but can't run yet. */
@Serializable
data object NearbySetupRoute

fun NavController.navigateToSecuritySettings() = navigate(SecuritySettingsRoute)

fun NavController.navigateToNetworkSettings() = navigate(NetworkSettingsRoute)

fun NavController.navigateToNotificationSettings() = navigate(NotificationSettingsRoute)

fun NavController.navigateToNearbySetup() = navigate(NearbySetupRoute) { launchSingleTop = true }

/**
 * @param onOpenFriends "Account & identity": the friends list.
 * @param onShowPairingCode the identity sheet's button: this phone's pairing code.
 * @param developerSection extra rows at the end of the list; supplied only by debug builds.
 */
fun NavGraphBuilder.settingsGraph(
    appVersion: String,
    onOpenSecurity: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSetUpNearby: () -> Unit,
    onOpenFriends: () -> Unit,
    onShowPairingCode: () -> Unit,
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
                onOpenNotifications = onOpenNotifications,
                onOpenFriends = onOpenFriends,
                onShowPairingCode = onShowPairingCode,
                onNotYetAvailable = onNotYetAvailable,
                developerSection = developerSection,
            )
        }
        composable<SecuritySettingsRoute> {
            SecuritySettingsScreen(onBack = onBack, onNotYetAvailable = onNotYetAvailable)
        }
        composable<NetworkSettingsRoute> {
            NetworkSettingsScreen(onBack = onBack, onSetUpNearby = onSetUpNearby)
        }
        composable<NotificationSettingsRoute> {
            NotificationSettingsScreen(onBack = onBack)
        }
    }
    // Outside the tab's graph: the chats list opens it too.
    composable<NearbySetupRoute> {
        NearbySetupScreen(onBack = onBack)
    }
}
