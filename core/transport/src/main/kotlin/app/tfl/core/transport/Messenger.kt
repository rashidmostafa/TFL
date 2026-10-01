package app.tfl.core.transport

import app.tfl.core.crypto.envelope.EnvelopeCodec
import app.tfl.core.crypto.envelope.OpenResult
import app.tfl.core.crypto.envelope.SealedEnvelope
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.database.repository.OutboxRepository
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.message.ChatMessage
import app.tfl.core.model.message.MessageBody
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageKind
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.session.AppSession
import app.tfl.core.session.TransportKeyring
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import app.tfl.core.model.Transport as TransportKind

/** Why a change wasn't sent. */
enum class SendRefusal {
    /** You blocked this friend. */
    BLOCKED,

    /** Their key changed and the new one isn't verified yet. */
    KEY_CHANGED,

    /** The friend or the message is gone. */
    NOT_FOUND,

    /** Empty, too long, or a timer TFL doesn't offer. */
    INVALID,

    /** Only your own messages can be edited (for 15 minutes) or deleted for everyone. */
    NOT_ALLOWED,

    /** TFL locked meanwhile. */
    LOCKED,
}

sealed interface SendOutcome {
    /** Stored and queued for the friend. [message] is the message it created or changed, if any. */
    data class Queued(val message: ChatMessage?) : SendOutcome

    data class Refused(val reason: SendRefusal) : SendOutcome
}

/** What a friend's envelope did here. */
enum class Applied {
    NEW_MESSAGE,
    CHANGE,

    /** Its id was seen before: it's acknowledged again but changes nothing. */
    DUPLICATE,

    /** Nothing to change, e.g. an edit of a message this phone doesn't have. */
    IGNORED,
}

/**
 * Chat on top of envelopes: what the chat screens do, and what friends' envelopes do here. Each
 * change a friend should see is sealed on this phone and queued in the outbox, in the same
 * database transaction as the change itself.
 *
 * The rules both phones apply are here and in [MessageRepository]: nothing goes to a blocked friend,
 * or to a changed key before it's verified; only the author edits (within 15 minutes) or deletes
 * for everyone; disappearing timers count down on both phones.
 */
@Singleton
class Messenger @Inject constructor(
    private val holder: DatabaseHolder,
    private val codec: EnvelopeCodec,
    private val keyring: TransportKeyring,
    private val session: AppSession,
    private val identities: IdentityRepository,
    private val contacts: ContactRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val outbox: OutboxRepository,
    private val feed: OutboxFeed,
    private val clock: DeviceClock,
) {
    private val mutex = Mutex()

    private class Refusal(val reason: SendRefusal) : Exception(null, null, false, false)

    private class Sender(val keys: TransportKeys, val friend: Contact)

    suspend fun sendText(contactId: Long, text: String, replyTo: MessageId? = null, scheduleAtMillis: Long? = null): SendOutcome = change { keys ->
        checkText(text)
        val sender = sender(keys, contactId)
        val now = clock.currentTimeMillis()
        val conversation = conversations.getOrCreate(contactId, now)
        val at = scheduleAtMillis?.coerceAtLeast(now) ?: now
        val scheduled = at > now
        val timer = conversation.expiresAfterSeconds
        // Sealed now, for the time it's due: it can go out even while TFL is locked.
        val sealed = codec.seal(keys, sender.friend.keys, MessageBody.Text(text, replyTo, timer), at)
        val message = messages.addOutgoing(conversation.id, sealed.msgId, text, replyTo, at, timer, at.takeIf { scheduled })
        queue(sender, sealed, notBeforeMillis = if (scheduled) at else 0)
        conversations.touch(conversation.id, now)
        message
    }

    /** Your own text, not deleted, within 15 minutes of being written, and not still waiting for its time. */
    fun canEdit(message: ChatMessage, nowMillis: Long): Boolean =
        message.outgoing && message.kind == MessageKind.TEXT && !message.deleted &&
            (message.scheduledForMillis ?: Long.MIN_VALUE) <= nowMillis &&
            nowMillis - message.createdAtMillis in 0..MessageLimits.EDIT_WINDOW_MILLIS

    fun canDeleteForEveryone(message: ChatMessage): Boolean = message.outgoing && message.kind == MessageKind.TEXT && !message.deleted

    /** A signed edit record goes to the friend; later edits replace earlier ones on both phones. */
    suspend fun edit(messageId: Long, text: String): SendOutcome = change { keys ->
        checkText(text)
        val message = messages.get(messageId) ?: refuse(SendRefusal.NOT_FOUND)
        val now = clock.currentTimeMillis()
        if (!canEdit(message, now)) refuse(SendRefusal.NOT_ALLOWED)
        val sender = sender(keys, contactOf(message))
        val number = message.editNumber + 1
        messages.applyEdit(message.conversationId, message.msgId, outgoing = true, text, number, now)
        queue(sender, codec.seal(keys, sender.friend.keys, MessageBody.Edit(message.msgId, text, number), now))
        messages.get(messageId)
    }

    /** Sets your reaction on a message, or removes it with a null [emoji]. */
    suspend fun react(messageId: Long, emoji: String?): SendOutcome = change { keys ->
        if (emoji != null && (emoji.isEmpty() || emoji.length > MessageLimits.MAX_EMOJI_CHARS)) refuse(SendRefusal.INVALID)
        val message = messages.get(messageId) ?: refuse(SendRefusal.NOT_FOUND)
        if (message.kind != MessageKind.TEXT || message.deleted) refuse(SendRefusal.NOT_ALLOWED)
        val sender = sender(keys, contactOf(message))
        val now = clock.currentTimeMillis()
        messages.setReaction(message.conversationId, message.msgId, fromMe = true, emoji, now)
        queue(sender, codec.seal(keys, sender.friend.keys, MessageBody.Reaction(message.msgId, emoji), now))
        messages.get(messageId)
    }

    /**
     * Erases your message here and asks the friend's phone to erase it too. Best effort: a phone
     * that never connects again keeps its copy. If it hadn't left yet, it never will.
     */
    suspend fun deleteForEveryone(messageId: Long): SendOutcome = change { keys ->
        val message = messages.get(messageId) ?: refuse(SendRefusal.NOT_FOUND)
        if (!canDeleteForEveryone(message)) refuse(SendRefusal.NOT_ALLOWED)
        val sender = sender(keys, contactOf(message))
        messages.deleteForEveryone(message.conversationId, message.msgId, outgoing = true)
        outbox.remove(listOf(message.msgId))
        queue(sender, codec.seal(keys, sender.friend.keys, MessageBody.Delete(message.msgId), clock.currentTimeMillis()))
        null
    }

    /** Removes a message from this phone only; a message not yet delivered still goes. */
    suspend fun deleteForMe(messageId: Long) = mutex.withLock { messages.deleteForMe(messageId) }

    /** Hands the transport the queue as it now stands. */
    private suspend fun publish(keys: TransportKeys) = feed.publish(OutboxFeed.Snapshot(keys.signPublicKey, outbox.all()))

    /** Sets the conversation's disappearing timer on both phones: new messages use it. */
    suspend fun setTimer(contactId: Long, seconds: Int): SendOutcome = change { keys ->
        if (seconds !in MessageLimits.TIMER_SECONDS) refuse(SendRefusal.INVALID)
        val sender = sender(keys, contactId)
        val now = clock.currentTimeMillis()
        val conversation = conversations.getOrCreate(contactId, now)
        if (conversation.expiresAfterSeconds == seconds) return@change null
        // Newer than any change already known, so the friend's phone applies it too.
        val at = maxOf(now, conversation.timerChangedAtMillis + 1)
        conversations.setTimer(conversation.id, seconds, at)
        val sealed = codec.seal(keys, sender.friend.keys, MessageBody.Timer(seconds), at)
        messages.addTimerChange(conversation.id, sealed.msgId, seconds, at, receivedAtMillis = null)
        queue(sender, sealed)
        conversations.touch(conversation.id, now)
        null
    }

    /** Changes a scheduled message still waiting for its time: its text, and when it goes. */
    suspend fun reschedule(messageId: Long, text: String, atMillis: Long): SendOutcome = change { keys ->
        checkText(text)
        rescheduleLocked(keys, waitingMessage(messageId), text, atMillis)
    }

    /** Sends a scheduled message now instead of at its time. */
    suspend fun sendNow(messageId: Long): SendOutcome = change { keys ->
        val message = waitingMessage(messageId)
        rescheduleLocked(keys, message, checkNotNull(message.text), clock.currentTimeMillis())
    }

    /** Deletes a scheduled message before it goes. */
    suspend fun cancelScheduled(messageId: Long): SendOutcome = change { _ ->
        val message = waitingMessage(messageId)
        outbox.remove(listOf(message.msgId))
        messages.deleteForMe(message.id)
        null
    }

    /**
     * Applies an envelope from friend [senderId], opened and checked by the transport. A receipt
     * marks messages delivered; anything else counts once by its id.
     */
    suspend fun apply(opened: OpenResult.Opened, senderId: Long, receivedAtMillis: Long, via: TransportKind): Applied = mutex.withLock {
        holder.transaction { applyLocked(opened, senderId, receivedAtMillis, via) }
    }

    private suspend fun applyLocked(opened: OpenResult.Opened, senderId: Long, receivedAtMillis: Long, via: TransportKind): Applied {
        val body = opened.body
        if (body is MessageBody.Receipt) {
            val conversation = conversations.forContact(senderId) ?: return Applied.IGNORED
            outbox.delivered(senderId, body.delivered)
            messages.markDelivered(conversation.id, body.delivered, receivedAtMillis, via)
            return Applied.CHANGE
        }
        val keepUntil = opened.createdAtMillis + MessageLimits.MAX_AGE_MILLIS
        if (!outbox.firstSeen(opened.msgId, codec.keyId(opened.sender.signPublicKey), keepUntil)) return Applied.DUPLICATE
        val conversation = conversations.getOrCreate(senderId, receivedAtMillis)
        return when (body) {
            is MessageBody.Text -> {
                val expiresAt = body.expiresAfterSeconds.takeIf { it > 0 }?.let { receivedAtMillis + it * 1_000L }
                // Its timer ran out while TFL was locked: it's gone without ever being shown.
                if (expiresAt != null && expiresAt <= clock.currentTimeMillis()) return Applied.IGNORED
                messages.addIncoming(conversation.id, opened.msgId, body.text, body.replyTo, opened.createdAtMillis, receivedAtMillis, body.expiresAfterSeconds, via)
                    ?: return Applied.DUPLICATE
                conversations.touch(conversation.id, receivedAtMillis)
                Applied.NEW_MESSAGE
            }
            is MessageBody.Edit ->
                messages.applyEdit(conversation.id, body.target, outgoing = false, body.text, body.editNumber, opened.createdAtMillis).changed()
            is MessageBody.Delete -> messages.deleteForEveryone(conversation.id, body.target, outgoing = false).changed()
            is MessageBody.Reaction ->
                messages.setReaction(conversation.id, body.target, fromMe = false, body.emoji, opened.createdAtMillis).changed()
            is MessageBody.Timer -> {
                val applied = conversations.applyTimer(conversation.id, body.expiresAfterSeconds, opened.createdAtMillis)
                if (applied) messages.addTimerChange(conversation.id, opened.msgId, body.expiresAfterSeconds, opened.createdAtMillis, receivedAtMillis)
                applied.changed()
            }
            is MessageBody.Receipt -> Applied.IGNORED
        }
    }

    /** One of this phone's queued envelopes went out. */
    suspend fun recordSent(msgId: MessageId, atMillis: Long, via: TransportKind, nextAttemptAtMillis: Long) = mutex.withLock {
        holder.transaction {
            messages.markSent(listOf(msgId), atMillis, via)
            outbox.markSent(msgId, atMillis, nextAttemptAtMillis)
        }
    }

    /**
     * Seals queued envelopes again for friends whose key changed and is now verified. A new key
     * means a new install that never saw the old messages, so texts and timer changes are sealed
     * again as they stand; queued reactions, edits and deletes are dropped.
     */
    suspend fun resealForNewKeys() = mutex.withLock {
        val keys = keys() ?: return@withLock
        val changed = holder.transaction {
            var changed = false
            val friends = HashMap<Long, Contact?>()
            for (item in outbox.all()) {
                val friend = friends.getOrPut(item.contactId) { contacts.get(item.contactId) } ?: continue
                if (!friend.canSend) continue
                val current = codec.keyId(friend.keys.signPublicKey)
                if (item.sealedFor == current) continue
                val message = messages.byMsgId(item.msgId)?.takeIf { it.outgoing && !it.deleted }
                val body = when (message?.kind) {
                    MessageKind.TEXT -> message.text?.let { MessageBody.Text(it, message.replyTo, message.expiresAfterSeconds) }
                    MessageKind.TIMER_CHANGED -> MessageBody.Timer(message.expiresAfterSeconds)
                    null -> null
                }
                changed = true
                if (body == null) {
                    outbox.remove(listOf(item.msgId))
                    continue
                }
                val sealed = codec.seal(keys, friend.keys, body, item.createdAtMillis, item.msgId)
                outbox.replace(item.msgId, sealed.bytes, current, item.createdAtMillis, item.notBeforeMillis)
            }
            changed
        }
        if (changed) publish(keys)
    }

    /**
     * Erases messages whose timer ran out, forgets ids too old to be replayed, and marks what
     * couldn't be delivered within 30 days.
     */
    suspend fun tidy(nowMillis: Long) = mutex.withLock {
        holder.transaction {
            messages.deleteExpired(nowMillis)
            outbox.forgetSeenBefore(nowMillis)
            messages.markFailed(outbox.giveUpOlderThan(nowMillis - MessageLimits.MAX_AGE_MILLIS))
        }
        keyring.keys.value?.let { publish(it) }
    }

    private suspend fun change(block: suspend (TransportKeys) -> ChatMessage?): SendOutcome = mutex.withLock {
        try {
            val keys = keys() ?: return@withLock SendOutcome.Refused(SendRefusal.LOCKED)
            val message = holder.transaction { block(keys) }
            publish(keys)
            SendOutcome.Queued(message)
        } catch (e: Refusal) {
            SendOutcome.Refused(e.reason)
        } catch (e: DatabaseLockedException) {
            SendOutcome.Refused(SendRefusal.LOCKED)
        }
    }

    /** The unlocked profile's keys, asked for again if the transport stopped and wiped them. */
    private suspend fun keys(): TransportKeys? {
        val keys = keyring.keys.value ?: run {
            session.refreshTransportKeys()
            keyring.keys.value
        } ?: return null
        val identity = identities.get() ?: return null
        return keys.takeIf { it.signPublicKey.contentEquals(identity.signPublicKey) }
    }

    private suspend fun sender(keys: TransportKeys, contactId: Long?): Sender {
        val friend = contactId?.let { contacts.get(it) } ?: refuse(SendRefusal.NOT_FOUND)
        if (friend.blocked) refuse(SendRefusal.BLOCKED)
        if (!friend.canSend) refuse(SendRefusal.KEY_CHANGED)
        return Sender(keys, friend)
    }

    private suspend fun contactOf(message: ChatMessage): Long? = conversations.get(message.conversationId)?.contactId

    private suspend fun waitingMessage(messageId: Long): ChatMessage {
        val message = messages.get(messageId) ?: refuse(SendRefusal.NOT_FOUND)
        val waitingUntil = message.scheduledForMillis
        if (waitingUntil == null || waitingUntil <= clock.currentTimeMillis() || message.status != DeliveryStatus.QUEUED) {
            refuse(SendRefusal.NOT_ALLOWED)
        }
        return message
    }

    private suspend fun rescheduleLocked(keys: TransportKeys, message: ChatMessage, text: String, atMillis: Long): ChatMessage {
        val sender = sender(keys, contactOf(message))
        val now = clock.currentTimeMillis()
        val at = atMillis.coerceAtLeast(now)
        val scheduled = at > now
        val changed = messages.reschedule(message.id, text, at, scheduled) ?: refuse(SendRefusal.NOT_ALLOWED)
        // The time is signed, so it's sealed again.
        val sealed = codec.seal(keys, sender.friend.keys, MessageBody.Text(text, message.replyTo, message.expiresAfterSeconds), at, message.msgId)
        outbox.replace(message.msgId, sealed.bytes, codec.keyId(sender.friend.keys.signPublicKey), at, if (scheduled) at else 0)
        return changed
    }

    private suspend fun queue(sender: Sender, sealed: SealedEnvelope, notBeforeMillis: Long = 0) = outbox.enqueue(
        msgId = sealed.msgId,
        contactId = sender.friend.id,
        envelope = sealed.bytes,
        sealedFor = codec.keyId(sender.friend.keys.signPublicKey),
        createdAtMillis = sealed.createdAtMillis,
        notBeforeMillis = notBeforeMillis,
    )

    private fun checkText(text: String) {
        if (text.isBlank() || text.length > MessageLimits.MAX_TEXT_CHARS) refuse(SendRefusal.INVALID)
    }

    private fun refuse(reason: SendRefusal): Nothing = throw Refusal(reason)

    private fun Boolean.changed() = if (this) Applied.CHANGE else Applied.IGNORED
}
