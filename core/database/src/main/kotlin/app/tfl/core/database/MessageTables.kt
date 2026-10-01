package app.tfl.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One conversation per friend. Deleted with the friend. */
@Entity(
    tableName = "conversations",
    foreignKeys = [ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["contactId"], unique = true)],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    /** The disappearing timer for new messages; 0 = off. */
    val expiresAfterSeconds: Int = 0,
    /** When the timer last changed, by either phone: an older change arriving late is ignored. */
    val timerChangedAtMillis: Long = 0,
    /** Incoming messages received after this are unread. */
    val lastReadAtMillis: Long = 0,
    val updatedAtMillis: Long,
)

/**
 * A message, or a line saying the timer changed. Text is plain inside the encrypted database and
 * nowhere else; `secure_delete` overwrites deleted rows in the file.
 */
@Suppress("ArrayInDataClass") // compared by id, never by value
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(ConversationEntity::class, ["id"], ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["msgId"], unique = true), Index(value = ["conversationId", "sortAtMillis"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val msgId: ByteArray,
    val conversationId: Long,
    val outgoing: Boolean,
    /** A MessageKind name. */
    val kind: String,
    val text: String?,
    val replyTo: ByteArray?,
    /** When the author wrote it, on their clock (signed). */
    val createdAtMillis: Long,
    val receivedAtMillis: Long?,
    /**
     * Where it sits in the conversation, on this phone's clock: when written here, or when it
     * arrived. Another phone's clock running fast or slow can't shuffle the order.
     */
    val sortAtMillis: Long,
    /** A DeliveryStatus name, for outgoing messages. */
    val status: String?,
    /** A Transport name, once it travelled. */
    val transport: String?,
    val sentAtMillis: Long? = null,
    val deliveredAtMillis: Long? = null,
    val editNumber: Int = 0,
    val editedAtMillis: Long? = null,
    val deleted: Boolean = false,
    val expiresAfterSeconds: Int = 0,
    val expiresAtMillis: Long? = null,
    val scheduledForMillis: Long? = null,
)

@Entity(
    tableName = "reactions",
    primaryKeys = ["messageId", "fromMe"],
    foreignKeys = [ForeignKey(MessageEntity::class, ["id"], ["messageId"], onDelete = ForeignKey.CASCADE)],
)
data class ReactionEntity(val messageId: Long, val fromMe: Boolean, val emoji: String, val updatedAtMillis: Long)

/** An envelope waiting for its friend: sealed, so only they can open it. */
@Suppress("ArrayInDataClass")
@Entity(
    tableName = "outbox",
    foreignKeys = [ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["msgId"], unique = true), Index(value = ["contactId"])],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val msgId: ByteArray,
    val contactId: Long,
    val envelope: ByteArray,
    /** The key id of the friend's identity key it's sealed to: after a key change, it's sealed again. */
    val sealedFor: ByteArray,
    val createdAtMillis: Long,
    /** Not sent before this time (scheduled messages); 0 = right away. */
    val notBeforeMillis: Long = 0,
    val attempts: Int = 0,
    val nextAttemptAtMillis: Long = 0,
    /** When it last left for the friend; cleared by their receipt, which removes the row. */
    val sentAtMillis: Long? = null,
)

/** Ids already received, kept 30 days to refuse replays. */
@Suppress("ArrayInDataClass")
@Entity(
    tableName = "seen_messages",
    indices = [Index(value = ["msgId"], unique = true), Index(value = ["keepUntilMillis"])],
)
data class SeenMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val msgId: ByteArray,
    val senderKeyId: ByteArray,
    val keepUntilMillis: Long,
)

@Dao
abstract class ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAtMillis DESC")
    abstract fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract fun observe(id: Long): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    abstract suspend fun get(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE contactId = :contactId")
    abstract suspend fun forContact(contactId: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE contactId = :contactId")
    abstract fun observeForContact(contactId: Long): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(conversation: ConversationEntity): Long

    @Update
    abstract suspend fun update(conversation: ConversationEntity)

    @Transaction
    open suspend fun getOrCreate(contactId: Long, nowMillis: Long): ConversationEntity =
        forContact(contactId) ?: ConversationEntity(contactId = contactId, updatedAtMillis = nowMillis).let { it.copy(id = insert(it)) }

    /** Unread incoming messages per conversation, ignoring those already expired. */
    @Query(
        """
        SELECT c.id AS conversationId, COUNT(m.id) AS unread FROM conversations c
        LEFT JOIN messages m ON m.conversationId = c.id AND m.outgoing = 0 AND m.kind = 'TEXT'
            AND m.receivedAtMillis > c.lastReadAtMillis AND (m.expiresAtMillis IS NULL OR m.expiresAtMillis > :nowMillis)
        GROUP BY c.id
        """,
    )
    abstract fun observeUnread(nowMillis: Long): Flow<List<UnreadCount>>
}

data class UnreadCount(val conversationId: Long, val unread: Int)

@Dao
abstract class MessageDao {
    /**
     * A conversation's messages in order, without expired ones or scheduled ones not yet due. A
     * due scheduled message shows (queued) even before it leaves.
     */
    @Query(
        """
        SELECT * FROM messages WHERE conversationId = :conversationId $SHOWN
        ORDER BY sortAtMillis, id
        """,
    )
    abstract fun observeConversation(conversationId: Long, nowMillis: Long): Flow<List<MessageEntity>>

    /** Scheduled messages not yet due, soonest first. */
    @Query(
        """
        SELECT * FROM messages WHERE conversationId = :conversationId AND scheduledForMillis > :nowMillis
            AND status = 'QUEUED' ORDER BY scheduledForMillis, id
        """,
    )
    abstract fun observeScheduled(conversationId: Long, nowMillis: Long): Flow<List<MessageEntity>>

    /** Each conversation's last shown message. */
    @Query(
        """
        SELECT * FROM messages WHERE id IN (
            SELECT (SELECT m.id FROM messages m WHERE m.conversationId = c.id $SHOWN ORDER BY m.sortAtMillis DESC, m.id DESC LIMIT 1)
            FROM conversations c
        )
        """,
    )
    abstract fun observeLatest(nowMillis: Long): Flow<List<MessageEntity>>

    @Query("SELECT r.* FROM reactions r JOIN messages m ON r.messageId = m.id WHERE m.conversationId = :conversationId")
    abstract fun observeReactions(conversationId: Long): Flow<List<ReactionEntity>>

    @Query("SELECT * FROM messages WHERE msgId = :msgId")
    abstract suspend fun byMsgId(msgId: ByteArray): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    abstract suspend fun get(id: Long): MessageEntity?

    /** -1 when a message with this msgId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(message: MessageEntity): Long

    @Update
    abstract suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE messages SET status = 'SENT', sentAtMillis = :atMillis, transport = :transport WHERE msgId IN (:msgIds) AND status = 'QUEUED'")
    abstract suspend fun markSent(msgIds: List<ByteArray>, atMillis: Long, transport: String)

    /** Only this conversation's messages: a friend's receipt can't touch messages sent to anyone else. */
    @Query(
        """
        UPDATE messages SET status = 'DELIVERED', deliveredAtMillis = :atMillis,
            sentAtMillis = COALESCE(sentAtMillis, :atMillis), transport = COALESCE(transport, :transport)
        WHERE conversationId = :conversationId AND msgId IN (:msgIds) AND outgoing = 1 AND status IN ('QUEUED', 'SENT')
        """,
    )
    abstract suspend fun markDelivered(conversationId: Long, msgIds: List<ByteArray>, atMillis: Long, transport: String)

    @Query("UPDATE messages SET status = 'FAILED' WHERE msgId IN (:msgIds) AND status IN ('QUEUED', 'SENT')")
    abstract suspend fun markFailed(msgIds: List<ByteArray>)

    /** Removes expired messages; `secure_delete` overwrites them in the file. */
    @Query("DELETE FROM messages WHERE expiresAtMillis IS NOT NULL AND expiresAtMillis <= :nowMillis")
    abstract suspend fun deleteExpired(nowMillis: Long): Int

    @Upsert
    abstract suspend fun upsertReaction(reaction: ReactionEntity)

    @Query("DELETE FROM reactions WHERE messageId = :messageId AND fromMe = :fromMe")
    abstract suspend fun deleteReaction(messageId: Long, fromMe: Boolean)

    /** The soonest time a shown message expires or a scheduled one falls due, after [nowMillis]. */
    @Query(
        """
        SELECT MIN(t) FROM (
            SELECT MIN(expiresAtMillis) AS t FROM messages WHERE expiresAtMillis > :nowMillis
            UNION ALL SELECT MIN(scheduledForMillis) FROM messages WHERE scheduledForMillis > :nowMillis
        )
        """,
    )
    abstract fun observeNextChange(nowMillis: Long): Flow<Long?>

    private companion object {
        /** Not expired, and not a scheduled message still waiting for its time. */
        const val SHOWN = "AND (scheduledForMillis IS NULL OR scheduledForMillis <= :nowMillis) " +
            "AND (expiresAtMillis IS NULL OR expiresAtMillis > :nowMillis)"
    }
}

@Dao
abstract class OutboxDao {
    @Query("SELECT * FROM outbox ORDER BY createdAtMillis, id")
    abstract fun observeAll(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY createdAtMillis, id")
    abstract suspend fun all(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE contactId = :contactId ORDER BY createdAtMillis, id")
    abstract suspend fun forContact(contactId: Long): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE msgId = :msgId")
    abstract suspend fun byMsgId(msgId: ByteArray): OutboxEntity?

    @Insert
    abstract suspend fun insert(entry: OutboxEntity): Long

    @Update
    abstract suspend fun update(entry: OutboxEntity)

    @Query("DELETE FROM outbox WHERE msgId IN (:msgIds)")
    abstract suspend fun deleteByMsgIds(msgIds: List<ByteArray>)

    @Query("DELETE FROM outbox WHERE contactId = :contactId AND msgId IN (:msgIds)")
    abstract suspend fun deleteForContact(contactId: Long, msgIds: List<ByteArray>)

    @Query("SELECT * FROM outbox WHERE createdAtMillis < :cutoffMillis")
    abstract suspend fun olderThan(cutoffMillis: Long): List<OutboxEntity>
}

@Dao
abstract class SeenMessageDao {
    /** -1 when the id was seen before. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(seen: SeenMessageEntity): Long

    @Query("SELECT COUNT(*) FROM seen_messages WHERE msgId = :msgId")
    abstract suspend fun count(msgId: ByteArray): Int

    @Query("SELECT msgId FROM seen_messages")
    abstract suspend fun all(): List<ByteArray>

    @Query("DELETE FROM seen_messages WHERE keepUntilMillis < :nowMillis")
    abstract suspend fun deleteExpired(nowMillis: Long): Int
}
