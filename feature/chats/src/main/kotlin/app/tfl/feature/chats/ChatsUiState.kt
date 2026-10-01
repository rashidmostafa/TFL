package app.tfl.feature.chats

import androidx.compose.runtime.Immutable
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.contact.Contact

/** How far you can trust a friend's keys. */
enum class ContactTrust { VERIFIED, UNVERIFIED, KEY_CHANGED }

val Contact.trust: ContactTrust
    get() = when {
        keyChanged -> ContactTrust.KEY_CHANGED
        isVerified -> ContactTrust.VERIFIED
        else -> ContactTrust.UNVERIFIED
    }

/** Nearby as the chats list shows it. */
sealed interface NearbyState {
    data object Off : NearbyState

    /** Switched on, but a permission ([permitted] false) or a switch (Bluetooth, Location) is missing. */
    data class NeedsSetup(val permitted: Boolean) : NearbyState

    data class On(val friendsNearby: Int) : NearbyState
}

/** The last line of a conversation, as its row shows it. */
sealed interface ThreadPreview {
    data class Text(val text: String, val mine: Boolean) : ThreadPreview
    data class Deleted(val mine: Boolean) : ThreadPreview
    data class Timer(val seconds: Int, val mine: Boolean) : ThreadPreview
    data object Empty : ThreadPreview
}

/** One conversation in the chats list. */
@Immutable
data class ThreadItem(
    val contactId: Long,
    val name: String,
    val trust: ContactTrust,
    val blocked: Boolean,
    val preview: ThreadPreview,
    val atMillis: Long?,
    val unread: Int,
    /** Delivery of your last message, when the last message is yours. */
    val lastStatus: DeliveryStatus?,
    val nearbyNow: Boolean,
)

/** A friend to start a chat with. */
@Immutable
data class FriendItem(val contactId: Long, val name: String, val trust: ContactTrust, val blocked: Boolean)

@Immutable
data class ChatsUiState(
    val nearby: NearbyState,
    val threads: List<ThreadItem>,
    val friends: List<FriendItem>,
    val hasQuery: Boolean,
    val time: TimeContext,
) {
    val hasFriends: Boolean get() = friends.isNotEmpty()
}

/** Conversations whose friend's name or last message contains [query], ignoring case. */
internal fun List<ThreadItem>.matching(query: String): List<ThreadItem> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { thread ->
        thread.name.contains(needle, ignoreCase = true) ||
            (thread.preview as? ThreadPreview.Text)?.text?.contains(needle, ignoreCase = true) == true
    }
}
