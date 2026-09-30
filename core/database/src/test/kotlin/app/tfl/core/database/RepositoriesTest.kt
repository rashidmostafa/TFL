package app.tfl.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingValue
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.identity.Identity
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.testing.database.PlainDatabaseFactory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoriesTest {

    private val factory = PlainDatabaseFactory(ApplicationProvider.getApplicationContext())
    private val holder = DatabaseHolder(factory)
    private val settings = SettingsRepository(holder)
    private val identities = IdentityRepository(holder)
    private val key = ByteArray(32) { 1 }

    @After
    fun tearDown() = holder.close()

    @Test
    fun `settings read as defaults while locked`() = runTest {
        settings.observe(SettingKeys.AUTO_LOCK).test {
            assertEquals(AutoLockTimeout.IMMEDIATELY, awaitItem())
        }
        assertThrows(DatabaseLockedException::class.java) { holder.require() }
    }

    @Test
    fun `settings persist across lock and unlock`() = runTest {
        holder.open("profile.db", key)
        settings.setAll(
            SettingValue(SettingKeys.AUTO_LOCK, AutoLockTimeout.MINUTE_1),
            SettingValue(SettingKeys.BRIDGE_MODE, BridgeMode.SNOWFLAKE),
        )
        settings.set(SettingKeys.TOR_ENABLED, false)
        assertEquals(AutoLockTimeout.MINUTE_1, settings.get(SettingKeys.AUTO_LOCK))

        holder.close()
        holder.open("profile.db", key)
        assertEquals(BridgeMode.SNOWFLAKE, settings.get(SettingKeys.BRIDGE_MODE))
        assertEquals(false, settings.get(SettingKeys.TOR_ENABLED))
    }

    @Test
    fun `observers follow lock and unlock`() = runTest {
        holder.open("profile.db", key)
        settings.set(SettingKeys.AUTO_LOCK, AutoLockTimeout.MINUTES_5)
        holder.close()
        settings.observe(SettingKeys.AUTO_LOCK).test {
            assertEquals(AutoLockTimeout.IMMEDIATELY, awaitItem())
            holder.open("profile.db", key)
            assertEquals(AutoLockTimeout.MINUTES_5, awaitItem())
            holder.close()
            assertEquals(AutoLockTimeout.IMMEDIATELY, awaitItem())
        }
    }

    @Test
    fun `identity is saved and read back`() = runTest {
        holder.open("profile.db", key)
        assertNull(identities.get())
        val identity = Identity("Valkyrie-7", "F162BE7D2746777DB5A0FF5D6329EC0E", ByteArray(32) { 3 }, ByteArray(32) { 4 }, 42)
        identities.save(identity, derivationVersion = 1)
        assertEquals(identity, identities.get())
        assertEquals(listOf("F162", "BE7D", "2746", "777D", "B5A0", "FF5D", "6329", "EC0E"), identities.get()?.fingerprintGroups)
    }

    @Test
    fun `a wrong key doesn't open the database`() {
        holder.open("profile.db", key)
        holder.close()
        assertThrows(DatabaseKeyException::class.java) { holder.open("profile.db", ByteArray(32) { 2 }) }
    }

    @Test
    fun `raw key passphrase is SQLCipher's x-hex form`() {
        val passphrase = SqlCipherDatabaseFactory.rawKeyPassphrase(ByteArray(32) { it.toByte() })
        assertEquals("x'000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F'", passphrase.decodeToString())
    }
}
