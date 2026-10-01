package app.tfl.core.database

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The single row describing this phone's identity. Public keys only; private keys are never stored. */
@Entity(tableName = "identity")
class IdentityEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val displayName: String,
    /** 32 uppercase hex characters. */
    val fingerprint: String,
    val signPublicKey: ByteArray,
    val kexPublicKey: ByteArray,
    val createdAtMillis: Long,
    val derivationVersion: Int,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** Key → value settings, read and written through [app.tfl.core.database.repository.SettingsRepository]. */
@Entity(tableName = "settings")
class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identity WHERE id = ${IdentityEntity.SINGLETON_ID}")
    fun observe(): Flow<IdentityEntity?>

    @Query("SELECT * FROM identity WHERE id = ${IdentityEntity.SINGLETON_ID}")
    suspend fun get(): IdentityEntity?

    @Upsert
    suspend fun upsert(identity: IdentityEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)

    @Upsert
    suspend fun putAll(settings: List<SettingEntity>)
}

/**
 * Version history (schemas exported to core/database/schemas):
 * 1. identity and settings (Phase 1)
 * 2. contacts and their key history (Phase 2)
 * 3. conversations, messages, reactions, the outbox and seen message ids (Phase 3)
 */
@Database(
    entities = [
        IdentityEntity::class, SettingEntity::class, ContactEntity::class, ContactKeyEntity::class,
        ConversationEntity::class, MessageEntity::class, ReactionEntity::class, OutboxEntity::class, SeenMessageEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class TflDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun settingsDao(): SettingsDao
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun seenMessageDao(): SeenMessageDao
}
