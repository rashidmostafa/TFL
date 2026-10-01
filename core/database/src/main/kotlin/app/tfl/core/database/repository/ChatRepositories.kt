package app.tfl.core.database.repository

import app.tfl.core.database.ConversationDao
import app.tfl.core.database.ConversationEntity
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.MessageDao
import app.tfl.core.database.MessageEntity
import app.tfl.core.database.ReactionEntity
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.core.model.message.ChatMessage
import app.tfl.core.model.message.Conversation
import app.tfl.core.model.message.ConversationSummary
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageKind
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.message.Reaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Conversations: one per friend, with its disappearing timer and what's been read. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ConversationRepository @Inject constructor(private val holder: DatabaseHolder) {

    /** The chats list, newest first; empty while locked. Expired messages don't count. */
    fun summaries(nowMillis: Long): Flow<List<ConversationSummary>> = holder.database.flatMapLatest { database ->
        if (database == null) return@flatMapLatest flowOf(emptyList())
        combine(
            database.conversationDao().observeAll(),
            database.messageDao().observeLatest(nowMillis),
            database.conversationDao().observeUnread(nowMillis),
        ) { conversations, latest, unread ->
            val lastBy = latest.associateBy { it.conversationId }
            val unreadBy = unread.associate { it.conversationId to it.unread }
            conversations.map { conversation ->
                ConversationSummary(
                    id = conversation.id,
                    contactId = conversation.contactId,
                    expiresAfterSeconds = conversation.expiresAfterSeconds,
                    lastMessage = lastBy[conversation.id]?.toModel(emptyList()),
                    unreadCount = unreadBy[conversation.id] ?: 0,
                    updatedAtMillis = conversation.updatedAtMillis,
                )
            }
        }
    }

    fun conversation(id: Long): Flow<Conversation?> = holder.database.flatMapLatest { database ->
        database?.conversationDao()?.observe(id)?.map { it?.toModel() } ?: flowOf(null)
    }

    /** The conversation with a friend, once there is one. */
    fun withContact(contactId: Long): Flow<Conversation?> = holder.database.flatMapLatest { database ->
        database?.conversationDao()?.observeForContact(contactId)?.map { it?.toModel() } ?: flowOf(null)
    }

    suspend fun get(id: Long): Conversation? = dao().get(id)?.toModel()

    suspend fun forContact(contactId: Long): Conversation? = dao().forContact(contactId)?.toModel()

    suspend fun getOrCreate(contactId: Long, nowMillis: Long): Conversation = dao().getOrCreate(contactId, nowMillis).toModel()

    /** This phone changes the timer: it always applies, and counts as the newest change. */
    suspend fun setTimer(id: Long, expiresAfterSeconds: Int, nowMillis: Long) {
        require(expiresAfterSeconds in MessageLimits.TIMER_SECONDS) { "Not an offered timer" }
        update(id) { it.copy(expiresAfterSeconds = expiresAfterSeconds, timerChangedAtMillis = maxOf(nowMillis, it.timerChangedAtMillis + 1)) }
    }

    /** The friend changed the timer at [changedAtMillis]; ignored if a newer change is known. */
    suspend fun applyTimer(id: Long, expiresAfterSeconds: Int, changedAtMillis: Long): Boolean {
        require(expiresAfterSeconds in MessageLimits.TIMER_SECONDS) { "Not an offered timer" }
        val dao = dao()
        val conversation = dao.get(id) ?: return false
        if (changedAtMillis <= conversation.timerChangedAtMillis) return false
        dao.update(conversation.copy(expiresAfterSeconds = expiresAfterSeconds, timerChangedAtMillis = changedAtMillis))
        return true
    }

    suspend fun markRead(id: Long, atMillis: Long) = update(id) { it.copy(lastReadAtMillis = maxOf(it.lastReadAtMillis, atMillis)) }

    /** Moves the conversation to the top of the list. */
    suspend fun touch(id: Long, atMillis: Long) = update(id) { it.copy(updatedAtMillis = maxOf(it.updatedAtMillis, atMillis)) }

    private suspend fun update(id: Long, change: (ConversationEntity) -> ConversationEntity) {
        val dao = dao()
        dao.get(id)?.let { dao.update(change(it)) }
    }

    private fun dao(): ConversationDao = holder.require().conversationDao()

    private fun ConversationEntity.toModel() = Conversation(id, contactId, expiresAfterSeconds, timerChangedAtMillis, lastReadAtMillis, updatedAtMillis)
}

/**
 * Messages and reactions, with the rules both phones apply: only the author edits (within 15
 * minutes) or deletes for everyone, later edits win, and expired messages are erased.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MessageRepository @Inject constructor(private val holder: DatabaseHolder) {

    /** A conversation's shown messages in order, with reactions; expired ones left out. */
    fun messages(conversationId: Long, nowMillis: Long): Flow<List<ChatMessage>> = holder.database.flatMapLatest { database ->
        if (database == null) return@flatMapLatest flowOf(emptyList())
        combine(
            database.messageDao().observeConversation(conversationId, nowMillis),
            database.messageDao().observeReactions(conversationId),
        ) { messages, reactions ->
            val byMessage = reactions.groupBy { it.messageId }
            messages.map { it.toModel(byMessage[it.id].orEmpty()) }
        }
    }

    /** Messages waiting for their scheduled time, soonest first. */
    fun scheduled(conversationId: Long, nowMillis: Long): Flow<List<ChatMessage>> = holder.database.flatMapLatest { database ->
        database?.messageDao()?.observeScheduled(conversationId, nowMillis)?.map { list -> list.map { it.toModel(emptyList()) } } ?: flowOf(emptyList())
    }

    /**
     * The next time after [nowMillis] that what's shown changes by itself (a message expires or a
     * scheduled one falls due), so screens know when to look again; null when nothing is pending.
     */
    fun nextChange(nowMillis: Long): Flow<Long?> = holder.database.flatMapLatest { database ->
        database?.messageDao()?.observeNextChange(nowMillis) ?: flowOf(null)
    }

    suspend fun byMsgId(msgId: MessageId): ChatMessage? = dao().byMsgId(msgId.bytes)?.toModel(emptyList())

    suspend fun get(id: Long): ChatMessage? = dao().get(id)?.toModel(emptyList())

    suspend fun addOutgoing(
        conversationId: Long,
        msgId: MessageId,
        text: String,
        replyTo: MessageId?,
        createdAtMillis: Long,
        expiresAfterSeconds: Int,
        scheduledForMillis: Long? = null,
    ): ChatMessage {
        val entity = MessageEntity(
            msgId = msgId.bytes,
            conversationId = conversationId,
            outgoing = true,
            kind = MessageKind.TEXT.name,
            text = text,
            replyTo = replyTo?.bytes,
            createdAtMillis = createdAtMillis,
            receivedAtMillis = null,
            sortAtMillis = createdAtMillis,
            status = DeliveryStatus.QUEUED.name,
            transport = null,
            expiresAfterSeconds = expiresAfterSeconds,
            // The sender's countdown starts when it's written (or when it's due, if scheduled).
            expiresAtMillis = expiresAfterSeconds.takeIf { it > 0 }?.let { createdAtMillis + it * 1_000L },
            scheduledForMillis = scheduledForMillis,
        )
        val id = dao().insert(entity)
        check(id != -1L) { "Duplicate message id" }
        return entity.copy(id = id).toModel(emptyList())
    }

    /** Stores a received message; null when its id is already stored. */
    suspend fun addIncoming(
        conversationId: Long,
        msgId: MessageId,
        text: String,
        replyTo: MessageId?,
        createdAtMillis: Long,
        receivedAtMillis: Long,
        expiresAfterSeconds: Int,
        transport: Transport,
    ): ChatMessage? {
        val entity = MessageEntity(
            msgId = msgId.bytes,
            conversationId = conversationId,
            outgoing = false,
            kind = MessageKind.TEXT.name,
            text = text,
            replyTo = replyTo?.bytes,
            createdAtMillis = createdAtMillis,
            receivedAtMillis = receivedAtMillis,
            sortAtMillis = receivedAtMillis,
            status = null,
            transport = transport.name,
            expiresAfterSeconds = expiresAfterSeconds,
            // The recipient's countdown starts on arrival.
            expiresAtMillis = expiresAfterSeconds.takeIf { it > 0 }?.let { receivedAtMillis + it * 1_000L },
        )
        val id = dao().insert(entity)
        return if (id == -1L) null else entity.copy(id = id).toModel(emptyList())
    }

    /**
     * The line "the timer changed to N" in the conversation, set by this phone when
     * [receivedAtMillis] is null; false when its id is already stored.
     */
    suspend fun addTimerChange(conversationId: Long, msgId: MessageId, seconds: Int, createdAtMillis: Long, receivedAtMillis: Long?): Boolean =
        dao().insert(
            MessageEntity(
                msgId = msgId.bytes,
                conversationId = conversationId,
                outgoing = receivedAtMillis == null,
                kind = MessageKind.TIMER_CHANGED.name,
                text = null,
                replyTo = null,
                createdAtMillis = createdAtMillis,
                receivedAtMillis = receivedAtMillis,
                sortAtMillis = receivedAtMillis ?: createdAtMillis,
                status = null,
                transport = null,
                expiresAfterSeconds = seconds,
            ),
        ) != -1L

    suspend fun markSent(msgIds: List<MessageId>, atMillis: Long, transport: Transport) {
        if (msgIds.isNotEmpty()) dao().markSent(msgIds.map { it.bytes }, atMillis, transport.name)
    }

    /** The friend in [conversationId] received these. */
    suspend fun markDelivered(conversationId: Long, msgIds: List<MessageId>, atMillis: Long, transport: Transport) {
        if (msgIds.isNotEmpty()) dao().markDelivered(conversationId, msgIds.map { it.bytes }, atMillis, transport.name)
    }

    suspend fun markFailed(msgIds: List<MessageId>) {
        if (msgIds.isNotEmpty()) dao().markFailed(msgIds.map { it.bytes })
    }

    /**
     * Applies an edit to [target] in [conversationId]: only a text by the same author ([outgoing]
     * says whether that's this phone), within 15 minutes of when it was written, and only a later
     * edit than the one shown. Returns whether it applied.
     */
    suspend fun applyEdit(conversationId: Long, target: MessageId, outgoing: Boolean, text: String, editNumber: Int, atMillis: Long): Boolean {
        val dao = dao()
        val message = dao.byMsgId(target.bytes) ?: return false
        val allowed = message.conversationId == conversationId && message.outgoing == outgoing && !message.deleted &&
            message.kind == MessageKind.TEXT.name && editNumber > message.editNumber &&
            atMillis - message.createdAtMillis <= MessageLimits.EDIT_WINDOW_MILLIS
        if (allowed) dao.update(message.copy(text = text, editNumber = editNumber, editedAtMillis = atMillis))
        return allowed
    }

    /** Deletes [target] for everyone, if its author ([outgoing]) asked. Its text is erased. */
    suspend fun deleteForEveryone(conversationId: Long, target: MessageId, outgoing: Boolean): Boolean {
        val dao = dao()
        val message = dao.byMsgId(target.bytes) ?: return false
        if (message.conversationId != conversationId || message.outgoing != outgoing || message.kind != MessageKind.TEXT.name) return false
        dao.update(message.copy(text = null, deleted = true, replyTo = null))
        dao.deleteReaction(message.id, fromMe = true)
        dao.deleteReaction(message.id, fromMe = false)
        return true
    }

    /** Removes a message from this phone only. */
    suspend fun deleteForMe(id: Long) = dao().delete(id)

    /** Sets or, with a null [emoji], removes a reaction on [target] in [conversationId]. */
    suspend fun setReaction(conversationId: Long, target: MessageId, fromMe: Boolean, emoji: String?, atMillis: Long): Boolean {
        val dao = dao()
        val message = dao.byMsgId(target.bytes) ?: return false
        if (message.conversationId != conversationId || message.deleted || message.kind != MessageKind.TEXT.name) return false
        if (emoji == null) dao.deleteReaction(message.id, fromMe) else dao.upsertReaction(ReactionEntity(message.id, fromMe, emoji, atMillis))
        return true
    }

    /**
     * Changes a scheduled message still waiting in the outbox: its text, and when it goes (now, to
     * send it at once). The caller re-seals it, since the time is signed. Returns the new message.
     */
    suspend fun reschedule(id: Long, text: String, atMillis: Long, scheduled: Boolean): ChatMessage? {
        val dao = dao()
        val message = dao.get(id) ?: return null
        if (message.scheduledForMillis == null || message.status != DeliveryStatus.QUEUED.name) return null
        val changed = message.copy(
            text = text,
            createdAtMillis = atMillis,
            sortAtMillis = atMillis,
            scheduledForMillis = atMillis.takeIf { scheduled },
            expiresAtMillis = message.expiresAfterSeconds.takeIf { it > 0 }?.let { atMillis + it * 1_000L },
        )
        dao.update(changed)
        return changed.toModel(emptyList())
    }

    /** Erases messages whose timer ran out; returns how many. */
    suspend fun deleteExpired(nowMillis: Long): Int = dao().deleteExpired(nowMillis)

    private fun dao(): MessageDao = holder.require().messageDao()
}

internal fun MessageEntity.toModel(reactions: List<ReactionEntity>) = ChatMessage(
    id = id,
    msgId = MessageId(msgId),
    conversationId = conversationId,
    outgoing = outgoing,
    kind = enumValues<MessageKind>().firstOrNull { it.name == kind } ?: MessageKind.TEXT,
    text = text,
    replyTo = replyTo?.let(::MessageId),
    createdAtMillis = createdAtMillis,
    receivedAtMillis = receivedAtMillis,
    sortAtMillis = sortAtMillis,
    status = status?.let { value -> enumValues<DeliveryStatus>().firstOrNull { it.name == value } },
    transport = transport?.let { value -> enumValues<Transport>().firstOrNull { it.name == value } },
    sentAtMillis = sentAtMillis,
    deliveredAtMillis = deliveredAtMillis,
    editNumber = editNumber,
    editedAtMillis = editedAtMillis,
    deleted = deleted,
    expiresAfterSeconds = expiresAfterSeconds,
    expiresAtMillis = expiresAtMillis,
    scheduledForMillis = scheduledForMillis,
    reactions = reactions.map { Reaction(it.fromMe, it.emoji) }.sortedBy { !it.fromMe },
)
