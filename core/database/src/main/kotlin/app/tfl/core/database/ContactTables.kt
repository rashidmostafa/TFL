package app.tfl.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A friend added by an in-person QR scan. Public keys only: TFL never holds a friend's private keys.
 * Enum columns hold enum names (see [app.tfl.core.database.repository.ContactRepository]).
 */
@Suppress("ArrayInDataClass") // compared by id, never by value
@Entity(tableName = "contacts", indices = [Index(value = ["signPublicKey"], unique = true)])
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The name in their pairing code. */
    val displayName: String,
    val nickname: String?,
    val signPublicKey: ByteArray,
    val kexPublicKey: ByteArray,
    /** 32 uppercase hex characters. */
    val fingerprint: String,
    /** Empty until Phase 4. */
    val onionAddress: String?,
    /** How the current key arrived: a KeySource name. */
    val keySource: String,
    /** When the current key was first seen. */
    val keySinceMillis: Long,
    /** A Verification name. */
    val verification: String,
    /** A VerificationMethod name, while verified. */
    val verifiedBy: String?,
    val verifiedAtMillis: Long?,
    val firstSeenAtMillis: Long,
    val lastPairedAtMillis: Long,
    /** Set when their key changed, until the new key is verified. */
    val keyChangedAtMillis: Long?,
    val blocked: Boolean,
)

/** A key a friend used before their current one. Deleted with the friend. */
@Entity(
    tableName = "contact_keys",
    foreignKeys = [ForeignKey(ContactEntity::class, ["id"], ["contactId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("contactId")],
)
class ContactKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val signPublicKey: ByteArray,
    val kexPublicKey: ByteArray,
    val fingerprint: String,
    val firstSeenAtMillis: Long,
    val replacedAtMillis: Long,
    /** How the key that replaced it arrived: a KeySource name. */
    val replacedBy: String,
)

@Dao
abstract class ContactDao {
    @Query("SELECT * FROM contacts ORDER BY COALESCE(nickname, displayName) COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    abstract fun observe(id: Long): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE id = :id")
    abstract suspend fun get(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE signPublicKey = :signPublicKey")
    abstract suspend fun findByKey(signPublicKey: ByteArray): ContactEntity?

    @Query("SELECT * FROM contacts WHERE displayName = :displayName COLLATE NOCASE")
    abstract suspend fun findByDisplayName(displayName: String): List<ContactEntity>

    @Query("SELECT * FROM contact_keys WHERE contactId = :contactId ORDER BY replacedAtMillis DESC")
    abstract fun observeKeyHistory(contactId: Long): Flow<List<ContactKeyEntity>>

    @Insert
    abstract suspend fun insert(contact: ContactEntity): Long

    @Update
    abstract suspend fun update(contact: ContactEntity)

    @Insert
    protected abstract suspend fun insertPreviousKey(previous: ContactKeyEntity)

    @Query("DELETE FROM contact_keys WHERE contactId = :contactId")
    protected abstract suspend fun deleteKeyHistory(contactId: Long)

    @Query("DELETE FROM contacts WHERE id = :id")
    protected abstract suspend fun deleteContact(id: Long)

    /** Keeps the old key in the history and makes [replacement] current, in one transaction. */
    @Transaction
    open suspend fun replaceKeys(previous: ContactKeyEntity, replacement: ContactEntity) {
        insertPreviousKey(previous)
        update(replacement)
    }

    /** Removes a friend and every key they ever used. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteKeyHistory(id)
        deleteContact(id)
    }
}
