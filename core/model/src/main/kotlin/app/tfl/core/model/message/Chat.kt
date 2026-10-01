package app.tfl.core.model.message

import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport

/** A conversation line: a message, or a note that the disappearing timer changed. */
enum class MessageKind { TEXT, TIMER_CHANGED }

/** A reaction on a message: yours or your friend's (one each). */
data class Reaction(val fromMe: Boolean, val emoji: String)

/** A message as the chat shows it. */
data class ChatMessage(
    val id: Long,
    val msgId: MessageId,
    val conversationId: Long,
    val outgoing: Boolean,
    val kind: MessageKind,
    /** Null once deleted for everyone. */
    val text: String?,
    val replyTo: MessageId?,
    /** The sender's clock, as signed. */
    val createdAtMillis: Long,
    /** When this phone received it (incoming only). */
    val receivedAtMillis: Long?,
    /** Its place in the conversation, on this phone's clock: when written here, or when it arrived. */
    val sortAtMillis: Long,
    /** Outgoing only. */
    val status: DeliveryStatus?,
    /** How it travelled, once it left or arrived. */
    val transport: Transport?,
    val sentAtMillis: Long?,
    val deliveredAtMillis: Long?,
    val editNumber: Int,
    val editedAtMillis: Long?,
    val deleted: Boolean,
    val expiresAfterSeconds: Int,
    /** When it disappears, once its countdown started. */
    val expiresAtMillis: Long?,
    /** Set while it waits to be sent at this time. */
    val scheduledForMillis: Long?,
    val reactions: List<Reaction>,
) {
    val edited: Boolean get() = editNumber > 0
}

/** One conversation for the chats list; the friend's details come from the contacts. */
data class ConversationSummary(
    val id: Long,
    val contactId: Long,
    val expiresAfterSeconds: Int,
    val lastMessage: ChatMessage?,
    val unreadCount: Int,
    val updatedAtMillis: Long,
)

data class Conversation(
    val id: Long,
    val contactId: Long,
    /** The disappearing timer for new messages; 0 = off. */
    val expiresAfterSeconds: Int,
    val timerChangedAtMillis: Long,
    val lastReadAtMillis: Long,
    val updatedAtMillis: Long,
)

/** A sealed envelope waiting for its friend. */
class OutboxItem(
    val msgId: MessageId,
    val contactId: Long,
    /** Sealed: only the friend can open it. */
    val envelope: ByteArray,
    /** Which of the friend's keys it's sealed to. */
    val sealedFor: KeyId,
    val createdAtMillis: Long,
    /** Not sent before this time (scheduled messages); 0 = right away. */
    val notBeforeMillis: Long,
    val attempts: Int,
    val nextAttemptAtMillis: Long,
    /** When it last left, while waiting for the friend's receipt. */
    val sentAtMillis: Long?,
)
