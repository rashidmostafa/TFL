package app.tfl.core.database

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

@Database(entities = [IdentityEntity::class, SettingEntity::class], version = 1, exportSchema = true)
abstract class TflDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun settingsDao(): SettingsDao
}
