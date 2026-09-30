package app.tfl.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.security.AutoLockTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a phone: SQLCipher's native library only exists on Android. */
@RunWith(AndroidJUnit4::class)
class SqlCipherDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = SqlCipherDatabaseFactory(context)
    private val holder = DatabaseHolder(factory)
    private val name = "sqlcipher-test.db"
    private val key = ByteArray(32) { (it * 7).toByte() }

    @After
    fun tearDown() {
        holder.close()
        factory.delete(name)
    }

    @Test
    fun theFileIsEncryptedAtRest() {
        holder.open(name, key)
        runBlocking { SettingsRepository(holder).set(SettingKeys.AUTO_LOCK, AutoLockTimeout.MINUTE_1) }
        holder.close()
        val header = context.getDatabasePath(name).readBytes().copyOf(16).decodeToString()
        assertFalse("plain SQLite header found", header.startsWith("SQLite format 3"))
    }

    @Test
    fun onlyTheRightKeyOpensIt() {
        holder.open(name, key)
        runBlocking { SettingsRepository(holder).set(SettingKeys.AUTO_LOCK, AutoLockTimeout.MINUTES_5) }
        holder.close()
        assertThrows(DatabaseKeyException::class.java) { holder.open(name, ByteArray(32) { 9 }) }
        holder.open(name, key)
        assertEquals(AutoLockTimeout.MINUTES_5, runBlocking { SettingsRepository(holder).get(SettingKeys.AUTO_LOCK) })
    }

    @Test
    fun memorySecurityIsOn() {
        holder.open(name, key)
        val value = holder.require().openHelper.writableDatabase.query("PRAGMA cipher_memory_security").use { cursor ->
            cursor.moveToFirst()
            cursor.getString(0)
        }
        assertEquals("1", value)
    }
}
