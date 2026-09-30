package app.tfl.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.feature.chats.ChatsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ChatsRoute

fun NavGraphBuilder.chatsScreen(
    onOpenProfile: () -> Unit,
    onScan: () -> Unit,
    onNewChat: () -> Unit,
    onNotYetAvailable: () -> Unit,
) {
    composable<ChatsRoute> {
        ChatsScreen(onOpenProfile = onOpenProfile, onScan = onScan, onNewChat = onNewChat, onNotYetAvailable = onNotYetAvailable)
    }
}
