package app.tfl.core.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.pairing.InvalidCode
import app.tfl.core.crypto.pairing.PairingScan
import app.tfl.core.crypto.pairing.ScannedCode
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.Profile
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.DURESS_PIN
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two simulated phones pairing, each with its own vault and database; their clocks agree. */
@RunWith(AndroidJUnit4::class)
class PairingIdentityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeDeviceClock()
    private val alice = SessionFixture(context, phone = "alice", clock = clock)
    private val bob = SessionFixture(context, phone = "bob", clock = clock)

    @After
    fun tearDown() {
        alice.database.close()
        bob.database.close()
    }

    private suspend fun SessionFixture.onboard(name: String, decoy: Boolean = false) {
        session.commitOnboarding(
            draft(
                tools.newSeed(),
                displayName = name,
                duressPin = if (decoy) DURESS_PIN else null,
                duressMode = if (decoy) DuressMode.DECOY else DuressMode.NONE,
            ),
        )
    }

    private suspend fun SessionFixture.scan(text: String): ScannedCode = when (val scan = pairing.check(text)) {
        is PairingScan.Valid -> scan.code
        is PairingScan.Invalid -> throw AssertionError("refused: ${scan.reason}")
    }

    @Test
    fun `codes carry the unlocked identity, signed by it`() = runTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val seen = bob.scan(alice.pairing.newCode().text)

        val identity = checkNotNull(alice.identities.get())
        assertEquals("Alice", seen.displayName)
        assertArrayEquals(identity.signPublicKey, seen.keys.signPublicKey)
        assertArrayEquals(identity.kexPublicKey, seen.keys.kexPublicKey)
        assertEquals(identity.fingerprintHex, seen.keys.fingerprintHex)
        assertEquals(clock.wall, seen.createdAtMillis)
        assertNull(seen.answers)
    }

    @Test
    fun `three scans prove to both phones that each scanned the other`() = runTest {
        alice.onboard("Alice")
        bob.onboard("Bob")

        // 1. Bob scans Alice's code. On its own that proves nothing about Alice scanning Bob.
        val aliceSeenByBob = bob.scan(alice.pairing.newCode().text)
        assertEquals(PairingProof.ONE_WAY, bob.pairing.proofFor(aliceSeenByBob))

        // 2. Alice scans Bob's answer: Bob must have scanned her code. Bob doesn't know she scanned his.
        clock.advance(20_000)
        val bobSeenByAlice = alice.scan(bob.pairing.newCode(answering = aliceSeenByBob).text)
        assertEquals(PairingProof.THEY_SCANNED_ME, alice.pairing.proofFor(bobSeenByAlice))

        // 3. Bob scans Alice's answer to his answer: he knows both scans happened, and so does Alice.
        clock.advance(20_000)
        val aliceAgain = bob.scan(alice.pairing.newCode(answering = bobSeenByAlice).text)
        assertEquals(PairingProof.BOTH_VERIFIED, bob.pairing.proofFor(aliceAgain))
    }

    @Test
    fun `the phone that scanned first learns both are verified on the third scan`() = runTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val bobCode = bob.pairing.newCode()
        // Alice scans first, then shows her answer; Bob scans it and shows his answer to that.
        val bobSeenByAlice = alice.scan(bobCode.text)
        assertEquals(PairingProof.ONE_WAY, alice.pairing.proofFor(bobSeenByAlice))
        val aliceAnswer = bob.scan(alice.pairing.newCode(answering = bobSeenByAlice).text)
        assertEquals(PairingProof.THEY_SCANNED_ME, bob.pairing.proofFor(aliceAnswer))
        val bobAnswer = alice.scan(bob.pairing.newCode(answering = aliceAnswer).text)
        assertEquals(PairingProof.BOTH_VERIFIED, alice.pairing.proofFor(bobAnswer))
    }

    @Test
    fun `an answer counts only while the code it answers is under 5 minutes old`() = runTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val aliceCode = alice.pairing.newCode()
        clock.advance(4 * 60_000L)
        val answer = bob.pairing.newCode(answering = bob.scan(aliceCode.text))

        clock.advance(60_000L + 1)
        val late = alice.scan(answer.text) // Bob's code itself is only 1 minute old
        assertEquals(PairingProof.ONE_WAY, alice.pairing.proofFor(late))
        assertNull(alice.pairing.latestAnswerId())
    }

    @Test
    fun `an answer to another phone's code doesn't count`() = runTest {
        val carol = SessionFixture(context, phone = "carol", clock = clock)
        try {
            alice.onboard("Alice")
            bob.onboard("Bob")
            carol.onboard("Carol")
            val answerToAlice = bob.pairing.newCode(answering = bob.scan(alice.pairing.newCode().text))
            carol.pairing.newCode()
            assertEquals(PairingProof.ONE_WAY, carol.pairing.proofFor(carol.scan(answerToAlice.text)))
        } finally {
            carol.database.close()
        }
    }

    @Test
    fun `the decoy profile signs with the decoy identity and ignores answers to the real one`() = runTest {
        alice.onboard("Alice", decoy = true)
        bob.onboard("Bob")
        val real = checkNotNull(alice.identities.get())
        val answerToReal = bob.pairing.newCode(answering = bob.scan(alice.pairing.newCode().text))

        alice.session.lock()
        alice.session.unlock(DURESS_PIN.encodeToByteArray())
        assertEquals(Profile.DECOY, alice.session.profile)
        val decoy = checkNotNull(alice.identities.get())
        val seen = bob.scan(alice.pairing.newCode().text)
        assertArrayEquals(decoy.signPublicKey, seen.keys.signPublicKey)
        assertFalse(real.signPublicKey.contentEquals(seen.keys.signPublicKey))

        assertEquals(PairingProof.ONE_WAY, alice.pairing.proofFor(alice.scan(answerToReal.text)))
    }

    @Test
    fun `the latest code id is the newest code shown`() = runTest {
        alice.onboard("Alice")
        assertNull(alice.pairing.latestAnswerId())
        alice.pairing.newCode()
        val newest = alice.pairing.newCode()
        assertArrayEquals(newest.answerId, alice.pairing.latestAnswerId())
    }

    @Test
    fun `own codes, stale codes and a locked phone are refused`() = runTest {
        alice.onboard("Alice")
        bob.onboard("Bob")
        val code = alice.pairing.newCode()
        assertEquals(PairingScan.Invalid(InvalidCode.OwnCode), alice.pairing.check(code.text))

        clock.advance(5 * 60_000L + 1)
        assertEquals(PairingScan.Invalid(InvalidCode.Expired(5)), bob.pairing.check(code.text))

        alice.session.lock()
        assertTrue(runCatching { alice.pairing.newCode() }.exceptionOrNull() is DatabaseLockedException)
        assertTrue(runCatching { alice.pairing.check(code.text) }.exceptionOrNull() is DatabaseLockedException)
    }
}
