package app.tfl.feature.contacts.verify

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.Verification
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.session.SessionFixture
import app.tfl.feature.contacts.TestKeys
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.friend.FriendViewModel
import app.tfl.feature.contacts.qr.scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Alice and Bob compare safety numbers, each phone scanning the other's screen. */
@RunWith(AndroidJUnit4::class)
class SafetyNumberViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeDeviceClock()
    private val alice = SessionFixture(context, phone = "alice", clock = clock)
    private val bob = SessionFixture(context, phone = "bob", clock = clock)

    @After
    fun tearDown() {
        alice.database.close()
        bob.database.close()
    }

    /** Each has the other's real keys, unverified. Returns (Bob on Alice's phone, Alice on Bob's). */
    private suspend fun friends(): Pair<Long, Long> {
        alice.session.commitOnboarding(alice.draft(alice.tools.newSeed(), displayName = "Alice"))
        bob.session.commitOnboarding(bob.draft(bob.tools.newSeed(), displayName = "Bob"))
        val a = checkNotNull(alice.identities.get())
        val b = checkNotNull(bob.identities.get())
        val bobOnAlice = alice.contacts.recordPairing("Bob", ContactKeys(b.signPublicKey, b.kexPublicKey, b.fingerprintHex), false, clock.wall)
        val aliceOnBob = bob.contacts.recordPairing("Alice", ContactKeys(a.signPublicKey, a.kexPublicKey, a.fingerprintHex), false, clock.wall)
        return bobOnAlice.id to aliceOnBob.id
    }

    private fun SessionFixture.compareWith(contactId: Long) = SafetyNumberViewModel(
        SavedStateHandle(mapOf(FriendViewModel.CONTACT_ID_ARG to contactId)),
        identities,
        safetyNumbers,
        contacts,
        clock,
        Dispatchers.Unconfined,
    )

    private val SafetyNumberViewModel.screenCode: String get() = checkNotNull(uiState.value.code).scan()

    @Test
    fun `both phones show the same number and the same code`() = runTest {
        val (bobOnAlice, aliceOnBob) = friends()
        val onAlice = alice.compareWith(bobOnAlice)
        val onBob = bob.compareWith(aliceOnBob)
        assertEquals(12, onAlice.uiState.value.groups.size)
        assertEquals(onAlice.uiState.value.groups, onBob.uiState.value.groups)
        assertEquals(onAlice.screenCode, onBob.screenCode)
    }

    @Test
    fun `scanning their matching code verifies them on this phone only`() = runTest {
        val (bobOnAlice, aliceOnBob) = friends()
        val onAlice = alice.compareWith(bobOnAlice)
        val onBob = bob.compareWith(aliceOnBob)

        onBob.selectTab(CompareTab.SCAN)
        onBob.onScanned(onAlice.screenCode)
        assertEquals(CompareResult.MATCH, onBob.uiState.value.result)
        assertEquals(CompareTab.SHOW, onBob.uiState.value.tab) // so Alice can scan Bob's next
        assertEquals(Trust.VERIFIED, onBob.uiState.value.trust)
        assertEquals(VerificationMethod.SAFETY_NUMBER_QR, checkNotNull(bob.contacts.get(aliceOnBob)).verifiedBy)
        assertEquals(Verification.UNVERIFIED, checkNotNull(alice.contacts.get(bobOnAlice)).verification)
    }

    @Test
    fun `another pair's number is a mismatch, and other codes are named`() = runTest {
        val (_, aliceOnBob) = friends()
        val onBob = bob.compareWith(aliceOnBob)
        val aliceKey = checkNotNull(alice.identities.get()).signPublicKey
        val aliceAndCarol = alice.safetyNumbers.comparisonCode(alice.safetyNumbers.between(aliceKey, TestKeys.random(5).signPublicKey))

        onBob.onScanned(aliceAndCarol)
        assertEquals(CompareResult.MISMATCH, onBob.uiState.value.result)
        assertEquals(Verification.UNVERIFIED, checkNotNull(bob.contacts.get(aliceOnBob)).verification)

        onBob.onScanned(alice.pairing.newCode().text)
        assertEquals(CompareResult.NOT_A_COMPARISON_CODE, onBob.uiState.value.result)
    }

    @Test
    fun `reading the numbers aloud and confirming verifies them`() = runTest {
        val (_, aliceOnBob) = friends()
        bob.compareWith(aliceOnBob).markVerified()
        assertEquals(VerificationMethod.SAFETY_NUMBER_MANUAL, checkNotNull(bob.contacts.get(aliceOnBob)).verifiedBy)
    }

    @Test
    fun `a key change while comparing shows the new number`() = runTest {
        val (_, aliceOnBob) = friends()
        val onBob = bob.compareWith(aliceOnBob)
        val before = onBob.uiState.value.groups
        onBob.onScanned("TFL-SN1:not a real code")
        bob.contacts.changeKeys(aliceOnBob, TestKeys.random(9), KeySource.DEBUG_SIMULATION, clock.wall)

        assertNotEquals(before, onBob.uiState.value.groups)
        assertEquals(Trust.KEY_CHANGED, onBob.uiState.value.trust)
        assertNull(onBob.uiState.value.result)
    }
}
