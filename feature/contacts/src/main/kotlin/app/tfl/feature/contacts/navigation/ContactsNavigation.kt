package app.tfl.feature.contacts.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.feature.contacts.add.AddFriendScreen
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.debug.ContactsDebugTools
import app.tfl.feature.contacts.friend.ContactProfileScreen
import app.tfl.feature.contacts.friend.FriendAddedScreen
import app.tfl.feature.contacts.friend.KeyChangeWarningScreen
import app.tfl.feature.contacts.friends.FriendsScreen
import app.tfl.feature.contacts.preview.GroupJoinPreviewScreen
import app.tfl.feature.contacts.preview.RemoteWipePreviewScreen
import app.tfl.feature.contacts.verify.SafetyNumberScreen
import kotlinx.serialization.Serializable

@Serializable
data object FriendsRoute

/** @param scan opens on the camera instead of this phone's code. */
@Serializable
data class AddFriendRoute(val scan: Boolean = false)

@Serializable
data class FriendAddedRoute(val contactId: Long)

@Serializable
data class ContactProfileRoute(val contactId: Long)

@Serializable
data class SafetyNumberRoute(val contactId: Long)

@Serializable
data class KeyChangeRoute(val contactId: Long)

@Serializable
data object GroupJoinPreviewRoute

@Serializable
data object RemoteWipePreviewRoute

fun NavController.navigateToFriends() = navigate(FriendsRoute) { launchSingleTop = true }

fun NavController.navigateToAddFriend(scan: Boolean = false) = navigate(AddFriendRoute(scan)) { launchSingleTop = true }

/** Friends, pairing in person, profiles, safety numbers and key changes. */
fun NavGraphBuilder.contactsGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }

    /** Leaves a screen for the friend's profile: back to it if it's underneath, else replacing this screen. */
    fun toProfile(contactId: Long, replacing: Int) {
        if (navController.previousBackStackEntry?.destination?.hasRoute<ContactProfileRoute>() == true) {
            navController.popBackStack()
        } else {
            navController.navigate(ContactProfileRoute(contactId)) { popUpTo(replacing) { inclusive = true } }
        }
    }

    composable<FriendsRoute> {
        FriendsScreen(
            onBack = back,
            onAddFriend = { navController.navigateToAddFriend() },
            // A changed key is shown before anything else about them.
            onOpenFriend = { friend ->
                navController.navigate(if (friend.trust == Trust.KEY_CHANGED) KeyChangeRoute(friend.id) else ContactProfileRoute(friend.id))
            },
            onOpenGroupJoinPreview = { navController.navigate(GroupJoinPreviewRoute) },
            onOpenRemoteWipePreview = { navController.navigate(RemoteWipePreviewRoute) },
        )
    }
    composable<AddFriendRoute> { entry ->
        AddFriendScreen(
            onBack = back,
            onAdded = { id -> navController.navigate(FriendAddedRoute(id)) { popUpTo(entry.destination.id) { inclusive = true } } },
        )
    }
    composable<FriendAddedRoute> { entry ->
        FriendAddedScreen(
            onDone = back,
            onViewProfile = { id -> navController.navigate(ContactProfileRoute(id)) { popUpTo(entry.destination.id) { inclusive = true } } },
        )
    }
    composable<ContactProfileRoute> {
        ContactProfileScreen(
            onBack = back,
            onCompare = { id -> navController.navigate(SafetyNumberRoute(id)) },
            onReviewKeyChange = { id -> navController.navigate(KeyChangeRoute(id)) },
        )
    }
    composable<SafetyNumberRoute> {
        SafetyNumberScreen(onBack = back, onPairAgain = { navController.navigateToAddFriend(scan = true) })
    }
    composable<KeyChangeRoute> { entry ->
        KeyChangeWarningScreen(
            onBack = back,
            onVerifyAgain = { id -> navController.navigate(SafetyNumberRoute(id)) { popUpTo(entry.destination.id) { inclusive = true } } },
            onContinue = { id -> toProfile(id, replacing = entry.destination.id) },
        )
    }
    composable<GroupJoinPreviewRoute> { GroupJoinPreviewScreen(onBack = back) }
    composable<RemoteWipePreviewRoute> { RemoteWipePreviewScreen(onBack = back) }
    with(ContactsDebugTools) { debugDestinations(navController) }
}
