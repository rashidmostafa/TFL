package app.tfl.feature.chats.conversation

import androidx.compose.runtime.Immutable
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.core.model.message.Reaction
import app.tfl.feature.chats.ContactTrust
import app.tfl.feature.chats.TimeContext

/** The message a reply answers, shown above it. [text] is null when it's deleted or not on this phone. */
@Immutable
data class Quote(val fromMe: Boolean, val text: String?)

/** A message as its bubble shows it. */
@Immutable
data class MessageView(
    val id: Long,
    val outgoing: Boolean,
    /** Null once deleted for everyone. */
    val text: String?,
    val edited: Boolean,
    /** Its place in the conversation (this phone's clock). */
    val atMillis: Long,
    val status: DeliveryStatus?,
    val transport: Transport?,
    val quote: Quote?,
    val reactions: List<Reaction>,
    val disappearsAtMillis: Long?,
) {
    val deleted: Boolean get() = text == null
}

/** One line of the conversation. */
sealed interface ChatRow {
    val key: String

    data class Day(val atMillis: Long) : ChatRow {
        override val key get() = "day-$atMillis"
    }

    /** "You set disappearing messages to 1 hour." */
    data class TimerChanged(val id: Long, val seconds: Int, val mine: Boolean) : ChatRow {
        override val key get() = "timer-$id"
    }

    data class Message(val message: MessageView) : ChatRow {
        override val key get() = "message-${message.id}"
    }
}

/** Why nothing can be sent to this friend right now (Phase 2's sending rule). */
enum class SendBlock {
    /** You blocked them. */
    BLOCKED,

    /** Their key changed and the new one isn't verified yet. */
    KEY_CHANGED,

    /** They were deleted from this phone. */
    GONE,
}

@Immutable
data class ConversationUiState(
    val contactId: Long,
    val name: String = "",
    val trust: ContactTrust = ContactTrust.UNVERIFIED,
    /** Their key fingerprint in groups of four. */
    val fingerprint: List<String> = emptyList(),
    val nearbyNow: Boolean = false,
    val timerSeconds: Int = 0,
    val rows: List<ChatRow> = emptyList(),
    val block: SendBlock? = null,
    val replyingTo: Quote? = null,
    val editing: Boolean = false,
    val scheduledCount: Int = 0,
    val time: TimeContext,
    val loading: Boolean = true,
    /** The long-pressed message's menu. */
    val actions: MessageActions? = null,
    /** The message whose info sheet is open. */
    val info: MessageInfo? = null,
)

/** What a message's long-press menu offers. */
@Immutable
data class MessageActions(
    val message: MessageView,
    val canReply: Boolean,
    val canEdit: Boolean,
    /** Minutes left to edit it. */
    val editMinutesLeft: Int,
    val canDeleteForEveryone: Boolean,
    val myReaction: String?,
)

/** Everything the info sheet shows about one message. */
@Immutable
data class MessageInfo(
    val outgoing: Boolean,
    val status: DeliveryStatus?,
    val transport: Transport?,
    /** When it was written, on its author's clock (signed). */
    val writtenAtMillis: Long,
    val sentAtMillis: Long?,
    val deliveredAtMillis: Long?,
    val receivedAtMillis: Long?,
    val editedAtMillis: Long?,
    val scheduledForMillis: Long?,
    val disappearsAtMillis: Long?,
    /** The first 16 hex characters of its random id. */
    val shortId: String,
)

/** Something to tell the user once. */
sealed interface ChatNotice {
    data class Refused(val reason: app.tfl.core.transport.SendRefusal) : ChatNotice
    data object Copied : ChatNotice
    data object Scheduled : ChatNotice
}
