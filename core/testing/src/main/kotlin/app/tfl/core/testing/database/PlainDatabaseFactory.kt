package app.tfl.core.testing.database

import android.content.Context
import androidx.room.Room
import app.tfl.core.database.DatabaseFactory
import app.tfl.core.database.DatabaseKeyException
import app.tfl.core.database.OpenedDatabase
import app.tfl.core.database.TflDatabase

/**
 * Unencrypted Room databases for Robolectric tests (SQLCipher's native library can't run on the JVM).
 * Files persist across close and reopen like the real ones, and each remembers the key it was created
 * with, so opening with another key fails just as SQLCipher would.
 */
class PlainDatabaseFactory(private val context: Context) : DatabaseFactory {

    private val keys = mutableMapOf<String, ByteArray>()

    val names: Set<String> get() = keys.keys.toSet()

    override fun open(name: String, key: ByteArray): OpenedDatabase {
        val expected = keys.getOrPut(name) { key.copyOf() }
        if (!expected.contentEquals(key)) throw DatabaseKeyException(IllegalStateException("wrong key for $name"))
        // Queries run on the calling thread, so a test sees each write as soon as the call returns.
        val database = Room.databaseBuilder(context, TflDatabase::class.java, name)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        database.openHelper.writableDatabase
        return OpenedDatabase(database, key.copyOf())
    }

    override fun delete(name: String) {
        keys.remove(name)
        context.deleteDatabase(name)
    }
}
