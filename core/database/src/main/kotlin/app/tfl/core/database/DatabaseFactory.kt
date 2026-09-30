package app.tfl.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/** The database was opened with a key that doesn't match: the file can't be decrypted. */
class DatabaseKeyException(cause: Throwable) : Exception("The database key doesn't match", cause)

/** An open database plus the key bytes it keeps using; [close] closes it and wipes the key. */
class OpenedDatabase(val database: TflDatabase, private val keyMaterial: ByteArray) {
    fun close() {
        database.close()
        keyMaterial.fill(0)
    }
}

interface DatabaseFactory {
    /** @throws DatabaseKeyException if [key] doesn't open an existing file. */
    fun open(name: String, key: ByteArray): OpenedDatabase

    /** Deletes a database file and its journal. */
    fun delete(name: String)
}

/**
 * SQLCipher-encrypted Room databases. The 32-byte key is passed in SQLCipher's raw-key form
 * `x'…64 hex…'`, built as bytes (never a String), which skips SQLCipher's PBKDF2: the key is already
 * random. Rollback-journal mode keeps a single connection and no `-wal`/`-shm` files.
 */
@Singleton
class SqlCipherDatabaseFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) : DatabaseFactory {

    private val nativeLibrary by lazy { System.loadLibrary("sqlcipher") }

    override fun open(name: String, key: ByteArray): OpenedDatabase {
        nativeLibrary
        val passphrase = rawKeyPassphrase(key)
        val database = Room.databaseBuilder(context, TflDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(passphrase, MemorySecurityHook, false))
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        try {
            database.openHelper.writableDatabase // opens now, so a wrong key fails here
        } catch (e: Exception) {
            database.close()
            passphrase.fill(0)
            throw DatabaseKeyException(e)
        }
        return OpenedDatabase(database, passphrase)
    }

    override fun delete(name: String) {
        context.deleteDatabase(name)
    }

    /**
     * Asks SQLCipher to zero memory it frees, so decrypted pages don't linger, and to overwrite
     * deleted rows inside the file, so a deleted friend's keys don't stay in free pages.
     */
    private object MemorySecurityHook : SQLiteDatabaseHook {
        override fun preKey(connection: SQLiteConnection) = Unit
        override fun postKey(connection: SQLiteConnection) {
            connection.executeRaw("PRAGMA cipher_memory_security = ON", null, null)
            connection.executeRaw("PRAGMA secure_delete = ON", null, null)
        }
    }

    internal companion object {
        private val HEX = "0123456789ABCDEF".encodeToByteArray()

        fun rawKeyPassphrase(key: ByteArray): ByteArray {
            require(key.size == 32) { "Database key must be 32 bytes" }
            val out = ByteArray(key.size * 2 + 3)
            out[0] = 'x'.code.toByte()
            out[1] = '\''.code.toByte()
            key.forEachIndexed { index, byte ->
                out[2 + index * 2] = HEX[(byte.toInt() ushr 4) and 0xF]
                out[3 + index * 2] = HEX[byte.toInt() and 0xF]
            }
            out[out.size - 1] = '\''.code.toByte()
            return out
        }
    }
}
