package app.tfl.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.Verification
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.testing.database.PlainDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ContactRepositoryTest {

    private val factory = PlainDatabaseFactory(ApplicationProvider.getApplicationContext())
    private val holder = DatabaseHolder(factory)
    private val contacts = ContactRepository(holder)
    private val t0 = 1_790_000_000_000L

    private fun keys(seed: Int) = ContactKeys(
        signPublicKey = Random(seed).nextBytes(32),
        kexPublicKey = Random(seed + 1_000).nextBytes(32),
        fingerprintHex = "%032X".format(seed),
    )

    @Before
    fun open() = holder.open("tfl-contacts-test.db", ByteArray(32) { 3 })

    @After
    fun tearDown() = holder.close()

    @Test
    fun `a one-way scan adds an unverified friend`() = runTest {
        val alice = contacts.recordPairing("Alice", keys(1), mutual = false, nowMillis = t0)
        assertEquals(Verification.UNVERIFIED, alice.verification)
        assertNull(alice.verifiedBy)
        assertEquals(t0, alice.firstSeenAtMillis)
        assertTrue(alice.canSend)
        assertArrayEquals(keys(1).signPublicKey, contacts.findByKey(keys(1).signPublicKey)?.keys?.signPublicKey)
    }

    @Test
    fun `a mutual scan adds a friend verified in person`() = runTest {
        val alice = contacts.recordPairing("Alice", keys(1), mutual = true, nowMillis = t0)
        assertEquals(Verification.VERIFIED, alice.verification)
        assertEquals(VerificationMethod.IN_PERSON_PAIRING, alice.verifiedBy)
        assertEquals(t0, alice.verifiedAtMillis)
    }

    @Test
    fun `scanning again upgrades but never downgrades, and refreshes the name`() = runTest {
        contacts.recordPairing("Alice", keys(1), mutual = false, nowMillis = t0)
        val upgraded = contacts.recordPairing("Alice", keys(1), mutual = true, nowMillis = t0 + 1)
        assertEquals(Verification.VERIFIED, upgraded.verification)

        val again = contacts.recordPairing("Alice M.", keys(1), mutual = false, nowMillis = t0 + 2)
        assertEquals(Verification.VERIFIED, again.verification)
        assertEquals("Alice M.", again.displayName)
        assertEquals(t0 + 2, again.lastPairedAtMillis)
        assertEquals(1, contacts.contacts.first().size)
    }

    @Test
    fun `a key change keeps the old key, unverifies and blocks sending until verified again`() = runTest {
        val alice = contacts.recordPairing("Alice", keys(1), mutual = true, nowMillis = t0)
        val changed = contacts.changeKeys(alice.id, keys(2), KeySource.DEBUG_SIMULATION, nowMillis = t0 + 10)

        assertArrayEquals(keys(2).signPublicKey, changed.keys.signPublicKey)
        assertEquals(Verification.UNVERIFIED, changed.verification)
        assertEquals(t0 + 10, changed.keyChangedAtMillis)
        assertEquals(KeySource.DEBUG_SIMULATION, changed.keySource)
        assertFalse(changed.canSend)

        val history = contacts.keyHistory(alice.id).first()
        assertEquals(1, history.size)
        assertArrayEquals(keys(1).signPublicKey, history.single().keys.signPublicKey)
        assertEquals(t0, history.single().firstSeenAtMillis)
        assertEquals(t0 + 10, history.single().replacedAtMillis)
        assertEquals(KeySource.DEBUG_SIMULATION, history.single().replacedBy)

        contacts.markVerified(alice.id, VerificationMethod.SAFETY_NUMBER_QR, nowMillis = t0 + 20)
        val verified = checkNotNull(contacts.get(alice.id))
        assertTrue(verified.canSend)
        assertFalse(verified.keyChanged)
        assertEquals(VerificationMethod.SAFETY_NUMBER_QR, verified.verifiedBy)
    }

    @Test
    fun `a new key-agreement key under a known identity key is treated as a key change`() = runTest {
        contacts.recordPairing("Alice", keys(1), mutual = true, nowMillis = t0)
        val odd = ContactKeys(keys(1).signPublicKey, keys(9).kexPublicKey, keys(1).fingerprintHex)
        val result = contacts.recordPairing("Alice", odd, mutual = false, nowMillis = t0 + 1)
        assertTrue(result.keyChanged)
        assertEquals(Verification.UNVERIFIED, result.verification)
        assertEquals(1, contacts.keyHistory(result.id).first().size)
    }

    @Test
    fun `a key can't move to a contact that doesn't own it`() = runTest {
        val alice = contacts.recordPairing("Alice", keys(1), mutual = false, nowMillis = t0)
        contacts.recordPairing("Bob", keys(2), mutual = false, nowMillis = t0)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { contacts.changeKeys(alice.id, keys(2), KeySource.IN_PERSON_SCAN, t0 + 1) }
        }
    }

    @Test
    fun `same name with another key is found, whatever the case`() = runTest {
        contacts.recordPairing("Elena", keys(1), mutual = false, nowMillis = t0)
        assertEquals(1, contacts.othersNamed("elena", keys(2)).size)
        assertTrue(contacts.othersNamed("Elena", keys(1)).isEmpty())
        assertTrue(contacts.othersNamed("Maya", keys(2)).isEmpty())
    }

    @Test
    fun `nickname, blocking and ordering`() = runTest {
        val zed = contacts.recordPairing("Zed", keys(1), mutual = false, nowMillis = t0)
        contacts.recordPairing("Bob", keys(2), mutual = false, nowMillis = t0)
        contacts.setNickname(zed.id, "  Aunt Zee ")
        assertEquals(listOf("Aunt Zee", "Bob"), contacts.contacts.first().map { it.name })
        contacts.setNickname(zed.id, "   ")
        assertNull(contacts.get(zed.id)?.nickname)

        contacts.setBlocked(zed.id, true)
        assertFalse(checkNotNull(contacts.get(zed.id)).canSend)
        contacts.setBlocked(zed.id, false)
        assertTrue(checkNotNull(contacts.get(zed.id)).canSend)
    }

    @Test
    fun `deleting a friend removes their keys and key history`() = runTest {
        val alice = contacts.recordPairing("Alice", keys(1), mutual = false, nowMillis = t0)
        contacts.changeKeys(alice.id, keys(2), KeySource.IN_PERSON_SCAN, t0 + 1)
        contacts.delete(alice.id)

        assertNull(contacts.get(alice.id))
        assertNull(contacts.findByKey(keys(2).signPublicKey))
        val remaining = holder.require().openHelper.readableDatabase.query("SELECT COUNT(*) FROM contact_keys").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(0, remaining)
    }

    @Test
    fun `nothing is readable while locked`() = runTest {
        contacts.recordPairing("Alice", keys(1), mutual = false, nowMillis = t0)
        holder.close()
        assertTrue(contacts.contacts.first().isEmpty())
        assertThrows(DatabaseLockedException::class.java) { kotlinx.coroutines.runBlocking { contacts.get(1) } }
    }
}
