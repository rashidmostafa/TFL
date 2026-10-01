package app.tfl.feature.chats.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.feature.chats.ChatsScreen
import app.tfl.feature.chats.conversation.ConversationNavigation
import app.tfl.feature.chats.conversation.ConversationScreen
import app.tfl.feature.chats.scheduled.ScheduledScreen
import kotlinx.serialization.Serializable

@Serializable
data object ChatsRoute

@Serializable
data class ConversationRoute(val contactId: Long)

@Serializable
data class ScheduledRoute(val contactId: Long)

fun NavController.navigateToConversation(contactId: Long) = navigate(ConversationRoute(contactId)) { launchSingleTop = true }

/** What chats need from other features; the app wires them. */
class ChatsLinks(
    /** Your own identity (the profile button). */
    val onOpenProfile: () -> Unit,
    /** Add a friend by scanning their code. */
    val onScan: () -> Unit,
    val onOpenFriend: (contactId: Long) -> Unit,
    val onVerifyFriend: (contactId: Long) -> Unit,
    val onReviewKeyChange: (contactId: Long) -> Unit,
    val onSetUpNearby: () -> Unit,
)

/** The chats list, conversations, and scheduled messages. */
fun NavGraphBuilder.chatsGraph(navController: NavController, links: ChatsLinks) {
    composable<ChatsRoute> {
        ChatsScreen(
            onOpenProfile = links.onOpenProfile,
            onScan = links.onScan,
            onOpenChat = navController::navigateToConversation,
            onResolveKey = links.onReviewKeyChange,
            onSetUpNearby = links.onSetUpNearby,
        )
    }
    composable<ConversationRoute> {
        ConversationScreen(
            ConversationNavigation(
                onBack = { navController.popBackStack() },
                onOpenProfile = links.onOpenFriend,
                onVerify = links.onVerifyFriend,
                onOpenScheduled = { navController.navigate(ScheduledRoute(it)) { launchSingleTop = true } },
            ),
        )
    }
    composable<ScheduledRoute> { ScheduledScreen(onBack = { navController.popBackStack() }) }
}
