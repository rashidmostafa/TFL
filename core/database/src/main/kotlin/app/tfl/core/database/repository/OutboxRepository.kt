package app.tfl.core.database.repository

import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.OutboxDao
import app.tfl.core.database.OutboxEntity
import app.tfl.core.database.SeenMessageEntity
import app.tfl.core.model.message.KeyId
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.OutboxItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the transport keeps across restarts: sealed envelopes waiting for their friend, and the ids
 * already received (kept 30 days, so a replayed message is recognised and refused).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class OutboxRepository @Inject constructor(private val holder: DatabaseHolder) {

    val items: Flow<List<OutboxItem>> = holder.database.flatMapLatest { database ->
        database?.outboxDao()?.observeAll()?.map { list -> list.map { it.toModel() } } ?: flowOf(emptyList())
    }

    suspend fun all(): List<OutboxItem> = dao().all().map { it.toModel() }

    suspend fun forContact(contactId: Long): List<OutboxItem> = dao().forContact(contactId).map { it.toModel() }

    suspend fun enqueue(msgId: MessageId, contactId: Long, envelope: ByteArray, sealedFor: KeyId, createdAtMillis: Long, notBeforeMillis: Long = 0) {
        dao().insert(
            OutboxEntity(
                msgId = msgId.bytes,
                contactId = contactId,
                envelope = envelope,
                sealedFor = sealedFor.bytes,
                createdAtMillis = createdAtMillis,
                notBeforeMillis = notBeforeMillis,
                nextAttemptAtMillis = notBeforeMillis,
            ),
        )
    }

    /**
     * Replaces an item's envelope: a scheduled message that changed, or one sealed again to the
     * friend's new key. It goes out afresh.
     */
    suspend fun replace(msgId: MessageId, envelope: ByteArray, sealedFor: KeyId, createdAtMillis: Long, notBeforeMillis: Long) {
        val dao = dao()
        val entry = dao.byMsgId(msgId.bytes) ?: return
        dao.update(
            entry.copy(
                envelope = envelope,
                sealedFor = sealedFor.bytes,
                createdAtMillis = createdAtMillis,
                notBeforeMillis = notBeforeMillis,
                nextAttemptAtMillis = notBeforeMillis,
                attempts = 0,
                sentAtMillis = null,
            ),
        )
    }

    /** It left for the friend; the next try is due at [nextAttemptAtMillis] unless their receipt comes first. */
    suspend fun markSent(msgId: MessageId, atMillis: Long, nextAttemptAtMillis: Long) {
        val dao = dao()
        val entry = dao.byMsgId(msgId.bytes) ?: return
        dao.update(entry.copy(sentAtMillis = atMillis, attempts = entry.attempts + 1, nextAttemptAtMillis = nextAttemptAtMillis))
    }

    /** Removes these (sent to anyone). */
    suspend fun remove(msgIds: List<MessageId>) {
        if (msgIds.isNotEmpty()) dao().deleteByMsgIds(msgIds.map { it.bytes })
    }

    /** [contactId]'s receipt arrived: these are done. Items queued for anyone else stay. */
    suspend fun delivered(contactId: Long, msgIds: List<MessageId>) {
        if (msgIds.isNotEmpty()) dao().deleteForContact(contactId, msgIds.map { it.bytes })
    }

    /** Removes items older than [cutoffMillis] and returns their ids: they could not be delivered. */
    suspend fun giveUpOlderThan(cutoffMillis: Long): List<MessageId> {
        val dao = dao()
        val stale = dao.olderThan(cutoffMillis).map { MessageId(it.msgId) }
        if (stale.isNotEmpty()) dao.deleteByMsgIds(stale.map { it.bytes })
        return stale
    }

    /** Records a received id; false when it was seen before (a duplicate or a replay). */
    suspend fun firstSeen(msgId: MessageId, sender: KeyId, keepUntilMillis: Long): Boolean =
        holder.require().seenMessageDao().insert(SeenMessageEntity(msgId = msgId.bytes, senderKeyId = sender.bytes, keepUntilMillis = keepUntilMillis)) != -1L

    suspend fun wasSeen(msgId: MessageId): Boolean = holder.require().seenMessageDao().count(msgId.bytes) > 0

    /** Every remembered id, for the transport's in-memory check while TFL is locked. */
    suspend fun seenIds(): List<MessageId> = holder.require().seenMessageDao().all().map(::MessageId)

    /** Forgets ids past their 30 days: messages that old are refused anyway. */
    suspend fun forgetSeenBefore(nowMillis: Long): Int = holder.require().seenMessageDao().deleteExpired(nowMillis)

    private fun dao(): OutboxDao = holder.require().outboxDao()

    private fun OutboxEntity.toModel() = OutboxItem(
        msgId = MessageId(msgId),
        contactId = contactId,
        envelope = envelope,
        sealedFor = KeyId(sealedFor),
        createdAtMillis = createdAtMillis,
        notBeforeMillis = notBeforeMillis,
        attempts = attempts,
        nextAttemptAtMillis = nextAttemptAtMillis,
        sentAtMillis = sentAtMillis,
    )
}
