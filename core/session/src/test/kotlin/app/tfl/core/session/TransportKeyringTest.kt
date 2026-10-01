package app.tfl.core.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.Profile
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.DURESS_PIN
import app.tfl.core.testing.session.SessionFixture.Companion.PIN
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** When the transport has keys (docs/SECURITY_DESIGN.md, "While TFL is locked"). */
@RunWith(AndroidJUnit4::class)
class TransportKeyringTest {

    private val fixture = SessionFixture(ApplicationProvider.getApplicationContext())
    private val session = fixture.session
    private val keyring = fixture.keyring

    @After
    fun tearDown() = fixture.database.close()

    private fun wiped(keys: TransportKeys) = keys.signSecretKey.all { it == 0.toByte() } && keys.kexSecretKey.all { it == 0.toByte() }

    private suspend fun onboard(duressMode: DuressMode = DuressMode.NONE) {
        session.start()
        val duressPin = DURESS_PIN.takeIf { duressMode != DuressMode.NONE }
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed(), duressPin = duressPin, duressMode = duressMode))
    }

    private fun held(): TransportKeys = checkNotNull(keyring.keys.value) { "no keys held" }

    @Test
    fun `unlocking hands over the profile's keys, and locking wipes them by default`() = runTest {
        onboard()
        val keys = held()
        val identity = checkNotNull(fixture.identities.get())
        assertArrayEquals(identity.signPublicKey, keys.signPublicKey)
        assertArrayEquals(identity.kexPublicKey, keys.kexPublicKey)

        session.lock()
        assertNull(keyring.keys.value)
        assertTrue(wiped(keys))

        session.unlock(PIN.encodeToByteArray())
        assertArrayEquals(identity.signPublicKey, held().signPublicKey)
    }

    @Test
    fun `while the background service runs, the keys outlive the lock`() = runTest {
        onboard()
        keyring.keepWhileLocked = true
        val keys = held()
        session.lock()
        assertSame(keys, keyring.keys.value)
        assertFalse(wiped(keys))

        // Unlocking the same identity again keeps what's held, so links stay up.
        session.unlock(PIN.encodeToByteArray())
        assertSame(keys, keyring.keys.value)
    }

    @Test
    fun `opening the decoy replaces the real keys and wipes them`() = runTest {
        onboard(DuressMode.DECOY)
        keyring.keepWhileLocked = true
        val real = held()
        session.lock()

        session.unlock(DURESS_PIN.encodeToByteArray())
        assertEquals(Profile.DECOY, session.profile)
        val decoy = held()
        assertArrayEquals(checkNotNull(fixture.identities.get()).signPublicKey, decoy.signPublicKey)
        assertFalse(real.signPublicKey.contentEquals(decoy.signPublicKey))
        assertTrue(wiped(real))
    }

    @Test
    fun `stopping the service wipes the keys until they're asked for again while unlocked`() = runTest {
        onboard()
        val keys = held()
        keyring.release()
        assertNull(keyring.keys.value)
        assertTrue(wiped(keys))

        session.refreshTransportKeys()
        assertArrayEquals(keys.signPublicKey, held().signPublicKey)

        keyring.release()
        session.lock()
        session.refreshTransportKeys()
        assertNull("locked: nothing to hand over", keyring.keys.value)
    }

    @Test
    fun `a wipe releases the keys`() = runTest {
        onboard(DuressMode.WIPE)
        keyring.keepWhileLocked = true
        val keys = held()
        session.lock()
        assertNotNull(keyring.keys.value)

        session.unlock(DURESS_PIN.encodeToByteArray())
        assertNull(keyring.keys.value)
        assertTrue(wiped(keys))
    }
}
