package app.tfl.feature.chats

import androidx.compose.runtime.Immutable
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport

enum class ConversationKind { DIRECT, GROUP, BROADCAST }

/** How far you can trust a conversation's keys. */
enum class ContactTrust { VERIFIED, UNVERIFIED, KEY_CHANGED }

enum class ChatFilter { ALL, DIRECT, GROUPS, BROADCASTS }

/** One row of the chats list. */
@Immutable
data class ConversationItem(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val initials: String,
    val preview: String,
    val time: String,
    val transport: Transport,
    val transportDetail: String? = null,
    /** Group member who wrote [preview], shown before it. */
    val previewAuthor: String? = null,
    /** Initials of two group members, drawn as a stacked avatar. */
    val memberInitials: List<String> = emptyList(),
    /** Shown under a broadcast channel's name. */
    val subtitle: String? = null,
    val unreadCount: Int = 0,
    val trust: ContactTrust = ContactTrust.VERIFIED,
    /** Delivery state of your last message, when it was yours. */
    val lastStatus: DeliveryStatus? = null,
    /** Short routing note on the right of the footer, e.g. "Via 1 relay". */
    val routeNote: String? = null,
)

@Immutable
data class ChatsUiState(
    val peerCount: Int,
    val torConnected: Boolean,
    val filter: ChatFilter,
    val counts: Map<ChatFilter, Int>,
    val broadcasts: List<ConversationItem>,
    val threads: List<ConversationItem>,
    val hasQuery: Boolean,
) {
    val isEmpty: Boolean get() = broadcasts.isEmpty() && threads.isEmpty()
}

internal fun ChatFilter.matches(kind: ConversationKind): Boolean = when (this) {
    ChatFilter.ALL -> true
    ChatFilter.DIRECT -> kind == ConversationKind.DIRECT
    ChatFilter.GROUPS -> kind == ConversationKind.GROUP
    ChatFilter.BROADCASTS -> kind == ConversationKind.BROADCAST
}

/** Conversations matching [filter] whose title or preview contains [query] (case-insensitive). */
internal fun List<ConversationItem>.filterBy(filter: ChatFilter, query: String): List<ConversationItem> {
    val needle = query.trim()
    return filter { filter.matches(it.kind) }
        .filter { needle.isEmpty() || it.title.contains(needle, ignoreCase = true) || it.preview.contains(needle, ignoreCase = true) }
}
