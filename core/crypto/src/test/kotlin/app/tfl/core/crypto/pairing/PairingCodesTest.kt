package app.tfl.core.crypto.pairing

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.identity.IdentityKeys
import app.tfl.core.model.proto.PairingPayload
import app.tfl.core.model.proto.SignedPairingCode
import app.tfl.core.model.proto.copy
import app.tfl.core.model.proto.signedPairingCode
import app.tfl.core.testing.crypto.JvmSodium
import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PairingCodesTest {

    private val sodium = JvmSodium.api
    private val fingerprints = Fingerprints(sodium)
    private val codes = PairingCodes(sodium, fingerprints)
    private val derivation = IdentityKeyDerivation(sodium)
    private val aliceSeed = ByteArray(32) { it.toByte() }
    private val alice: IdentityKeys = derivation.derive(aliceSeed)
    private val bob: IdentityKeys = derivation.derive(ByteArray(32) { (it + 100).toByte() })
    private val mallory: IdentityKeys = derivation.derive(ByteArray(32) { (it + 200).toByte() })
    private val now = 1_790_000_000_000L
    private val minute = 60_000L

    private fun parse(text: String, at: Long = now, own: ByteArray? = bob.signPublicKey) = codes.parse(text, at, own)

    private fun valid(scan: PairingScan): ScannedCode {
        assertTrue("expected a valid code, got $scan", scan is PairingScan.Valid)
        return (scan as PairingScan.Valid).code
    }

    private fun reason(scan: PairingScan): InvalidCode {
        assertTrue("expected a refused code", scan is PairingScan.Invalid)
        return (scan as PairingScan.Invalid).reason
    }

    private fun bytesOf(text: String): ByteArray = Base64.getUrlDecoder().decode(text.removePrefix(PairingCodes.PREFIX))

    private fun textOf(bytes: ByteArray): String = PairingCodes.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** A code whose payload is [change]d from a real one and signed by [signer], as a forger would. */
    private fun forged(signer: IdentityKeys, change: PairingPayload.Builder.() -> Unit): String {
        val original = SignedPairingCode.parseFrom(bytesOf(codes.create(alice, "Alice", now).text))
        val payload = PairingPayload.parseFrom(original.payload).toBuilder().apply(change).build().toByteArray()
        val message = "TFL-pairing-v1".encodeToByteArray() + 0.toByte() + payload
        return textOf(
            signedPairingCode {
                this.payload = ByteString.copyFrom(payload)
                signature = ByteString.copyFrom(sodium.signDetached(message, signer.signSecretKey))
            }.toByteArray(),
        )
    }

    @Test
    fun `a fresh code reads back as its owner's name and public keys`() {
        val issued = codes.create(alice, "Alice", now)
        val scanned = valid(parse(issued.text, now + 1_000))
        assertEquals("Alice", scanned.displayName)
        assertArrayEquals(alice.signPublicKey, scanned.keys.signPublicKey)
        assertArrayEquals(alice.kexPublicKey, scanned.keys.kexPublicKey)
        assertEquals(fingerprints.of(alice.signPublicKey), scanned.keys.fingerprintHex)
        assertEquals(now, scanned.createdAtMillis)
        assertArrayEquals(issued.answerId, scanned.answerId)
        assertNull(scanned.answers)
        assertTrue(issued.text.startsWith("TFL-PAIR1:"))
    }

    @Test
    fun `an answer names the exact code it answers`() {
        val aliceCode = codes.create(alice, "Alice", now)
        val bobAnswer = codes.create(bob, "Bob", now, answers = aliceCode.answerId)
        assertArrayEquals(aliceCode.answerId, valid(parse(bobAnswer.text, own = alice.signPublicKey)).answers)
    }

    @Test
    fun `every code is different, even from the same owner at the same moment`() {
        val first = codes.create(alice, "Alice", now)
        val second = codes.create(alice, "Alice", now)
        assertFalse(first.text == second.text)
        assertFalse(first.answerId.contentEquals(second.answerId))
    }

    @Test
    fun `flipping any single byte of a code gets it refused`() {
        val bytes = bytesOf(codes.create(alice, "Alice", now).text)
        for (index in bytes.indices) {
            val tampered = bytes.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertFalse("byte $index flipped", parse(textOf(tampered)) is PairingScan.Valid)
        }
    }

    @Test
    fun `changing the name or a key breaks the signature`() {
        val original = SignedPairingCode.parseFrom(bytesOf(codes.create(alice, "Alice", now).text))
        val payload = PairingPayload.parseFrom(original.payload)
        listOf(
            payload.copy { displayName = "Alicia" },
            payload.copy { signPublicKey = ByteString.copyFrom(mallory.signPublicKey) },
            payload.copy { kexPublicKey = ByteString.copyFrom(mallory.kexPublicKey) },
            payload.copy { createdAtMillis = now + minute },
        ).forEach { changed ->
            val swapped = original.copy { this.payload = changed.toByteString() }
            assertEquals(InvalidCode.BadSignature, reason(parse(textOf(swapped.toByteArray()))))
        }
    }

    @Test
    fun `a code claiming someone's key but signed by another key is refused`() {
        val claimsAlice = forged(mallory) { signPublicKey = ByteString.copyFrom(alice.signPublicKey) }
        assertEquals(InvalidCode.BadSignature, reason(parse(claimsAlice)))
    }

    @Test
    fun `other versions are refused`() {
        assertEquals(InvalidCode.UnsupportedVersion, reason(parse(forged(alice) { version = 2 })))
    }

    @Test
    fun `codes last five minutes, and far-future codes are refused`() {
        val text = codes.create(alice, "Alice", now).text
        valid(parse(text, now + 5 * minute))
        assertEquals(InvalidCode.Expired(minutesOld = 5), reason(parse(text, now + 5 * minute + 1)))
        assertEquals(InvalidCode.Expired(minutesOld = 60), reason(parse(text, now + 60 * minute)))

        val fromTheFuture = codes.create(alice, "Alice", now + 7 * minute).text
        valid(parse(codes.create(alice, "Alice", now + 5 * minute).text, now))
        assertEquals(InvalidCode.FromTheFuture(minutesAhead = 7), reason(parse(fromTheFuture, now)))
    }

    @Test
    fun `your own code is recognised`() {
        assertEquals(InvalidCode.OwnCode, reason(parse(codes.create(bob, "Bob", now).text, own = bob.signPublicKey)))
    }

    @Test
    fun `names that break the display-name rules are refused`() {
        listOf("Ele‮na", "Two\nlines", "", " Alice", "A".repeat(33), "Zero​space").forEach { name ->
            assertEquals("name \"$name\"", InvalidCode.BadName, reason(parse(forged(alice) { displayName = name })))
        }
        assertThrows(IllegalArgumentException::class.java) { codes.create(alice, "Ele‮na", now) }
    }

    @Test
    fun `anything that isn't a pairing code is told apart`() {
        assertEquals(InvalidCode.NotPairingCode, reason(parse("https://example.org")))
        assertEquals(InvalidCode.NotPairingCode, reason(parse(SafetyNumbers.PREFIX + "AAAA")))
        assertEquals(InvalidCode.Unreadable, reason(parse(PairingCodes.PREFIX + "not base64!")))
        assertEquals(InvalidCode.Unreadable, reason(parse(textOf(ByteArray(2_000)))))
        val text = codes.create(alice, "Alice", now).text
        assertFalse(parse(text.dropLast(20)) is PairingScan.Valid)
        assertEquals(InvalidCode.Unreadable, reason(parse(forged(alice) { nonce = ByteString.copyFrom(ByteArray(4)) })))
    }

    @Test
    fun `a code never contains a secret`() {
        val bytes = bytesOf(codes.create(alice, "Alice", now, answers = ByteArray(16)).text)
        val secrets = listOf(aliceSeed, alice.signSecretKey.copyOf(32), alice.kexSecretKey, alice.backupKey)
        secrets.forEach { secret ->
            // Any 8-byte window of a secret would do; none may appear anywhere in the code.
            for (start in 0..secret.size - 8) {
                val window = secret.copyOfRange(start, start + 8)
                assertFalse(bytes.asList().windowed(8).any { it.toByteArray().contentEquals(window) })
            }
        }
    }
}
