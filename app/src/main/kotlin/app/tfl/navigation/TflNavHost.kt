package app.tfl.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import app.tfl.debug.DebugTools
import app.tfl.feature.chats.navigation.ChatsLinks
import app.tfl.feature.chats.navigation.ChatsRoute
import app.tfl.feature.chats.navigation.chatsGraph
import app.tfl.feature.chats.navigation.navigateToConversation
import app.tfl.feature.contacts.navigation.contactsGraph
import app.tfl.feature.contacts.navigation.navigateToAddFriend
import app.tfl.feature.contacts.navigation.navigateToContactProfile
import app.tfl.feature.contacts.navigation.navigateToFriends
import app.tfl.feature.contacts.navigation.navigateToKeyChange
import app.tfl.feature.contacts.navigation.navigateToSafetyNumber
import app.tfl.feature.map.navigation.mapScreen
import app.tfl.feature.settings.navigation.navigateToNearbySetup
import app.tfl.feature.settings.navigation.navigateToNetworkSettings
import app.tfl.feature.settings.navigation.navigateToNotificationSettings
import app.tfl.feature.settings.navigation.navigateToSecuritySettings
import app.tfl.feature.settings.navigation.settingsGraph
import app.tfl.feature.tools.navigation.toolsScreen
import app.tfl.feature.vault.navigation.vaultScreen
import app.tfl.ui.TflAppState

/** Wires every feature's screens together. Features never navigate to each other directly. */
@Composable
fun TflNavHost(
    appState: TflAppState,
    appVersion: String,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    val openProfile = { appState.navigateTo(TopLevelDestination.SETTINGS) }
    val developerSection = remember(navController) { DebugTools.settingsSection(navController) }

    NavHost(navController = navController, startDestination = ChatsRoute, modifier = modifier) {
        chatsGraph(
            navController,
            ChatsLinks(
                onOpenProfile = openProfile,
                onScan = { navController.navigateToAddFriend(scan = true) },
                onOpenFriend = navController::navigateToContactProfile,
                onVerifyFriend = navController::navigateToSafetyNumber,
                onReviewKeyChange = navController::navigateToKeyChange,
                onSetUpNearby = navController::navigateToNearbySetup,
            ),
        )
        mapScreen(onOpenProfile = openProfile, onNotYetAvailable = onNotYetAvailable)
        vaultScreen(onOpenProfile = openProfile, onNotYetAvailable = onNotYetAvailable)
        toolsScreen(onOpenProfile = openProfile, onNotYetAvailable = onNotYetAvailable)
        settingsGraph(
            appVersion = appVersion,
            onOpenSecurity = navController::navigateToSecuritySettings,
            onOpenNetwork = navController::navigateToNetworkSettings,
            onOpenNotifications = navController::navigateToNotificationSettings,
            onSetUpNearby = navController::navigateToNearbySetup,
            onOpenFriends = navController::navigateToFriends,
            onShowPairingCode = { navController.navigateToAddFriend() },
            onBack = { navController.popBackStack() },
            onNotYetAvailable = onNotYetAvailable,
            developerSection = developerSection,
        )
        contactsGraph(navController, onMessage = navController::navigateToConversation)
        with(DebugTools) { debugDestinations(onBack = { navController.popBackStack() }) }
    }
}
