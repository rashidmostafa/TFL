package app.tfl.feature.contacts.add

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.pairing.InvalidCode
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.Verification
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.session.SessionFixture
import app.tfl.feature.contacts.TestKeys
import app.tfl.feature.contacts.qr.scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * Two simulated phones pairing through their Add friend screens. Each "scan" reads the other
 * phone's QR image through ZXing, as the camera would.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AddFriendViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeDeviceClock()
    private val alice = SessionFixture(context, phone = "alice", clock = clock)
    private val bob = SessionFixture(context, phone = "bob", clock = clock)
    private val phones = mutableListOf(alice, bob)
    private val screens = mutableListOf<AddFriendViewModel>()

    @After
    fun tearDown() = phones.forEach { it.database.close() }

    /**
     * runTest runs everything left on its virtual clock once the body ends; each screen's code
     * ticker would run forever, so the screens stop first.
     */
    private fun phonesTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            screens.forEach { it.stop() }
        }
    }

    private suspend fun SessionFixture.onboard(name: String) = session.commitOnboarding(draft(tools.newSeed(), displayName = name))

    /** Opens Add friend on this phone. */
    private fun SessionFixture.addFriend(scan: Boolean = false) = AddFriendViewModel(
        SavedStateHandle(mapOf(AddFriendViewModel.SCAN_ARG to scan)),
        identities,
        pairing,
        contacts,
        clock,
        Dispatchers.Unconfined,
    ).also {
        screens += it
        it.start()
    }

    private fun TestScope.eventsOf(flow: Flow<Long>): List<Long> = mutableListOf<Long>().also { events ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { events += it } }
    }

    /** What the other phone's camera reads off this screen. */
    private val AddFriendViewModel.screenCode: String get() = checkNotNull(uiState.value.code).matrix.scan()

    @Test
    fun `three scans verify both phones, whichever shows first`() = phonesTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val aliceScreen = alice.addFriend()
        val bobScreen = bob.addFriend(scan = true)
        val aliceAdded = eventsOf(aliceScreen.added)
        val bobAdded = eventsOf(bobScreen.added)

        // 1. Bob scans Alice: she's saved unverified, and Bob's screen shows his answer.
        bobScreen.onScanned(aliceScreen.screenCode)
        val bobStep = bobScreen.uiState.value.step as PairingStep.Answering
        assertEquals("Alice", bobStep.name)
        assertFalse(bobStep.verified)
        assertEquals(AddFriendTab.MY_CODE, bobScreen.uiState.value.tab)
        assertEquals(Verification.UNVERIFIED, checkNotNull(bob.contacts.get(bobStep.contactId)).verification)

        // 2. Alice scans Bob's answer: Bob scanned her, so she verifies him.
        aliceScreen.selectTab(AddFriendTab.SCAN)
        aliceScreen.onScanned(bobScreen.screenCode)
        val aliceStep = aliceScreen.uiState.value.step as PairingStep.Answering
        assertTrue(aliceStep.verified)
        val bobOnAlice = checkNotNull(alice.contacts.get(aliceStep.contactId))
        assertEquals(VerificationMethod.IN_PERSON_PAIRING, bobOnAlice.verifiedBy)
        assertArrayEquals(checkNotNull(bob.identities.get()).signPublicKey, bobOnAlice.keys.signPublicKey)

        // 3. Bob scans Alice's answer: both verified; Bob goes straight to "friend added".
        bobScreen.selectTab(AddFriendTab.SCAN)
        bobScreen.onScanned(aliceScreen.screenCode)
        assertEquals(listOf(bobStep.contactId), bobAdded)
        assertEquals(VerificationMethod.IN_PERSON_PAIRING, checkNotNull(bob.contacts.get(bobStep.contactId)).verifiedBy)

        // Alice's phone can't see the third scan: she taps Done.
        aliceScreen.done()
        assertEquals(listOf(aliceStep.contactId), aliceAdded)
    }

    @Test
    fun `stopping after two scans leaves the first scanner unverified`() = phonesTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val aliceScreen = alice.addFriend()
        val bobScreen = bob.addFriend(scan = true)
        bobScreen.onScanned(aliceScreen.screenCode)
        aliceScreen.onScanned(bobScreen.screenCode)

        val aliceOnBob = checkNotNull(bob.contacts.findByKey(checkNotNull(alice.identities.get()).signPublicKey))
        val bobOnAlice = checkNotNull(alice.contacts.findByKey(checkNotNull(bob.identities.get()).signPublicKey))
        assertEquals(Verification.UNVERIFIED, aliceOnBob.verification)
        assertEquals(Verification.VERIFIED, bobOnAlice.verification)
    }

    @Test
    fun `refused codes say why and save nothing`() = phonesTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val aliceScreen = alice.addFriend(scan = true)
        val bobCode = bob.addFriend().screenCode

        aliceScreen.onScanned("https://example.com/")
        assertEquals(InvalidCode.NotPairingCode, aliceScreen.uiState.value.problem)

        aliceScreen.onScanned(aliceScreen.screenCode)
        assertEquals(InvalidCode.OwnCode, aliceScreen.uiState.value.problem)

        aliceScreen.onScanned(tamperedSignature(bobCode))
        assertEquals(InvalidCode.BadSignature, aliceScreen.uiState.value.problem)

        clock.advance(5 * 60_000L + 1)
        aliceScreen.onScanned(bobCode)
        assertEquals(InvalidCode.Expired(5), aliceScreen.uiState.value.problem)

        assertTrue(alice.contacts.othersNamed("Bob", TestKeys.none).isEmpty())
        assertEquals(PairingStep.Ready, aliceScreen.uiState.value.step)

        // Coming back to the camera with the same stale code in view explains it again.
        aliceScreen.selectTab(AddFriendTab.MY_CODE)
        aliceScreen.selectTab(AddFriendTab.SCAN)
        assertNull(aliceScreen.uiState.value.problem)
        aliceScreen.onScanned(bobCode)
        assertEquals(InvalidCode.Expired(5), aliceScreen.uiState.value.problem)
    }

    @Test
    fun `a new key under a friend's name asks first, and same person records a key change`() = phonesTest {
        val elena = SessionFixture(context, phone = "elena", clock = clock).also { phones += it }
        bob.onboard("Bob")
        elena.onboard("Elena")
        val old = bob.contacts.recordPairing("Elena", TestKeys.random(1), mutual = true, nowMillis = clock.wall - 86_400_000)
        val bobScreen = bob.addFriend(scan = true)
        val elenaCode = elena.addFriend().screenCode

        bobScreen.onScanned(elenaCode)
        assertEquals("Elena", bobScreen.uiState.value.namesake)
        assertNull(bob.contacts.findByKey(checkNotNull(elena.identities.get()).signPublicKey))

        bobScreen.onNamesakeAnswer(samePerson = true)
        val updated = checkNotNull(bob.contacts.get(old.id))
        assertArrayEquals(checkNotNull(elena.identities.get()).signPublicKey, updated.keys.signPublicKey)
        assertTrue(updated.keyChanged)
        assertEquals(KeySource.IN_PERSON_SCAN, updated.keySource)
        assertEquals(1, bob.contacts.keyHistory(old.id).first().size)
        assertEquals(old.id, (bobScreen.uiState.value.step as PairingStep.Answering).contactId)
    }

    @Test
    fun `someone else adds a second friend with the same name, and cancelling saves nothing`() = phonesTest {
        val elena = SessionFixture(context, phone = "elena", clock = clock).also { phones += it }
        bob.onboard("Bob")
        elena.onboard("Elena")
        bob.contacts.recordPairing("Elena", TestKeys.random(1), mutual = false, nowMillis = clock.wall)
        val elenaScreen = elena.addFriend()

        val first = bob.addFriend(scan = true)
        first.onScanned(elenaScreen.screenCode)
        first.dismissNamesake()
        first.onScanned(elenaScreen.screenCode) // still in view: not asked again
        assertNull(first.uiState.value.namesake)
        assertEquals(1, bob.contacts.othersNamed("Elena", TestKeys.none).size)

        val second = bob.addFriend(scan = true)
        second.onScanned(elenaScreen.screenCode)
        second.onNamesakeAnswer(samePerson = false)
        assertEquals(2, bob.contacts.othersNamed("Elena", TestKeys.none).size)
    }

    @Test
    fun `a new code every 60 seconds, with a countdown`() = phonesTest {
        alice.onboard("Alice")
        val screen = alice.addFriend()
        val first = checkNotNull(screen.uiState.value.code)
        assertEquals(60, first.secondsLeft)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(59, checkNotNull(screen.uiState.value.code).secondsLeft)

        advanceTimeBy(59_000)
        runCurrent()
        val next = checkNotNull(screen.uiState.value.code)
        assertNotEquals(first.text, next.text)
        assertEquals(60, next.secondsLeft)
    }

    @Test
    fun `an answer stops after 5 minutes and the screen says so`() = phonesTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val bobScreen = bob.addFriend(scan = true)
        bobScreen.onScanned(alice.addFriend().screenCode)
        assertTrue(bobScreen.uiState.value.step is PairingStep.Answering)

        clock.advance(5 * 60_000L + 1)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(PairingStep.Ready, bobScreen.uiState.value.step)
        assertTrue(bobScreen.uiState.value.windowClosed)
    }

    /** The same code with the last signature byte changed. */
    private fun tamperedSignature(code: String): String {
        val prefix = "TFL-PAIR1:"
        val bytes = Base64.getUrlDecoder().decode(code.removePrefix(prefix))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
