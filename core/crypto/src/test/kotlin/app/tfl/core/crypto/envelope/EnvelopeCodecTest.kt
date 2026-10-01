package app.tfl.core.crypto.envelope

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.KeyId
import app.tfl.core.model.message.MessageBody
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.proto.Envelope
import app.tfl.core.testing.crypto.JvmSodium
import com.google.protobuf.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeCodecTest {

    private val sodium = JvmSodium.api
    private val codec = EnvelopeCodec(sodium)
    private val derivation = IdentityKeyDerivation(sodium)
    private val fingerprints = Fingerprints(sodium)
    private val now = 1_790_000_000_000L

    /** A phone's own keys, and how its friends know it. */
    private inner class Phone(seed: Int) {
        val keys: TransportKeys = derivation.derive(ByteArray(32) { seed.toByte() }).use { it.forTransport() }
        val contact = ContactKeys(keys.signPublicKey, keys.kexPublicKey, fingerprints.of(keys.signPublicKey))
    }

    private val alice = Phone(1)
    private val bob = Phone(2)
    private val carol = Phone(3)

    /** The friends a phone may hear from. */
    private fun friends(vararg phones: Phone): (KeyId) -> ContactKeys? = { id ->
        phones.firstOrNull { codec.keyId(it.keys.signPublicKey) == id }?.contact
    }

    private fun sealToBob(body: MessageBody = MessageBody.Text("hi"), at: Long = now) = codec.seal(alice.keys, bob.contact, body, at)

    private fun bobOpens(bytes: ByteArray, at: Long = now) = codec.open(bytes, bob.keys, friends(alice), at)

    private fun refusal(result: OpenResult) = (result as? OpenResult.Refused)?.reason

    private fun Envelope.edit(block: Envelope.Builder.() -> Unit): ByteArray = toBuilder().apply(block).build().toByteArray()

    @Test
    fun `every kind of message opens as sent, from the right sender`() {
        val target = codec.newMessageId()
        val bodies = listOf(
            MessageBody.Text("Are you near the water tower?"),
            MessageBody.Text("Yes", replyTo = target, expiresAfterSeconds = 3_600),
            MessageBody.Receipt(listOf(target, codec.newMessageId())),
            MessageBody.Reaction(target, "👍"),
            MessageBody.Reaction(target, null),
            MessageBody.Edit(target, "Yes, I am", editNumber = 1),
            MessageBody.Delete(target),
            MessageBody.Timer(300),
        )
        for (body in bodies) {
            val sealed = sealToBob(body)
            val opened = bobOpens(sealed.bytes, now + 1_000) as OpenResult.Opened
            assertEquals(body, opened.body)
            assertEquals(sealed.msgId, opened.msgId)
            assertEquals(now, opened.createdAtMillis)
            assertArrayEquals(alice.contact.signPublicKey, opened.sender.signPublicKey)
        }
    }

    @Test
    fun `lengths only show as padding buckets`() {
        fun payloadSize(text: String) = Envelope.parseFrom(sealToBob(MessageBody.Text(text)).bytes).payload.size()
        assertEquals(256 + SodiumApi.BOX_SEAL_BYTES, payloadSize("a"))
        assertEquals(payloadSize("a"), payloadSize("x".repeat(90)))
        assertEquals(1_024 + SodiumApi.BOX_SEAL_BYTES, payloadSize("x".repeat(300)))
        val longest = payloadSize("x".repeat(MessageLimits.MAX_TEXT_CHARS))
        assertTrue(longest - SodiumApi.BOX_SEAL_BYTES in EnvelopeCodec.BUCKETS && longest <= 16_384 + SodiumApi.BOX_SEAL_BYTES)
    }

    @Test
    fun `no change to any byte alters what the recipient reads`() {
        val body = MessageBody.Text("Keep relays powered")
        val sealed = sealToBob(body)
        for (i in sealed.bytes.indices) {
            val changed = sealed.bytes.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            when (val result = bobOpens(changed)) {
                // Only the relay fields (ttl, hop count) are unsigned: changing them changes nothing read.
                is OpenResult.Opened -> {
                    assertEquals("byte $i", body, result.body)
                    assertEquals("byte $i", sealed.msgId, result.msgId)
                    assertEquals("byte $i", now, result.createdAtMillis)
                }
                is OpenResult.Refused -> Unit
            }
        }
    }

    @Test
    fun `a message for Bob can't be opened by anyone else`() {
        val sealed = sealToBob()
        assertEquals(Refusal.NOT_FOR_ME, refusal(codec.open(sealed.bytes, carol.keys, friends(alice), now)))
        // Even with Carol's hint put on it, only Bob's key unseals it.
        val readdressed = Envelope.parseFrom(sealed.bytes).edit { recipientHint = ByteString.copyFrom(codec.hint(carol.keys.signPublicKey)) }
        assertEquals(Refusal.NOT_FOR_ME, refusal(codec.open(readdressed, carol.keys, friends(alice), now)))
    }

    @Test
    fun `a signed message re-sealed to someone else is caught`() {
        // Bob opens Alice's message and seals the same signed content to Carol, posing as Alice.
        val original = Envelope.parseFrom(sealToBob(MessageBody.Text("for Bob only")).bytes)
        val padded = checkNotNull(sodium.boxSealOpen(original.payload.toByteArray(), bob.keys.kexPublicKey, bob.keys.kexSecretKey))
        val forged = original.edit {
            recipientHint = ByteString.copyFrom(codec.hint(carol.keys.signPublicKey))
            payload = ByteString.copyFrom(sodium.boxSeal(padded, carol.keys.kexPublicKey))
        }
        assertEquals(Refusal.WRONG_RECIPIENT, refusal(codec.open(forged, carol.keys, friends(alice), now)))
    }

    @Test
    fun `strangers and forged signatures are refused`() {
        val fromStranger = codec.seal(carol.keys, bob.contact, MessageBody.Text("hi"), now)
        assertEquals(Refusal.UNKNOWN_SENDER, refusal(bobOpens(fromStranger.bytes)))

        // Carol claims to be Alice: Alice's key id, Carol's signature.
        val posing = TransportKeys(sodium, alice.keys.signPublicKey, carol.keys.signSecretKey, carol.keys.kexPublicKey, carol.keys.kexSecretKey)
        val forged = codec.seal(posing, bob.contact, MessageBody.Text("it's me, Alice"), now)
        assertEquals(Refusal.BAD_SIGNATURE, refusal(bobOpens(forged.bytes)))
    }

    @Test
    fun `the signed id and time must match the envelope's`() {
        val envelope = Envelope.parseFrom(sealToBob().bytes)
        val otherId = envelope.edit { msgId = ByteString.copyFrom(codec.newMessageId().bytes) }
        val otherTime = envelope.edit { createdAtMillis = now - 1 }
        assertEquals(Refusal.MISMATCH, refusal(bobOpens(otherId)))
        assertEquals(Refusal.MISMATCH, refusal(bobOpens(otherTime)))
    }

    @Test
    fun `the clock window is 30 days back and 10 minutes ahead`() {
        val oldest = now - MessageLimits.MAX_AGE_MILLIS
        val furthestAhead = now + MessageLimits.MAX_CLOCK_AHEAD_MILLIS
        assertTrue(bobOpens(sealToBob(at = oldest).bytes) is OpenResult.Opened)
        assertEquals(Refusal.TOO_OLD, refusal(bobOpens(sealToBob(at = oldest - 1).bytes)))
        assertTrue(bobOpens(sealToBob(at = furthestAhead).bytes) is OpenResult.Opened)
        assertEquals(Refusal.FROM_THE_FUTURE, refusal(bobOpens(sealToBob(at = furthestAhead + 1).bytes)))
    }

    @Test
    fun `content outside the limits is refused even when signed`() {
        assertEquals(Refusal.INVALID_CONTENT, refusal(bobOpens(sealToBob(MessageBody.Text("hi", expiresAfterSeconds = 7)).bytes)))
        assertEquals(Refusal.INVALID_CONTENT, refusal(bobOpens(sealToBob(MessageBody.Text("")).bytes)))
        assertEquals(Refusal.INVALID_CONTENT, refusal(bobOpens(sealToBob(MessageBody.Timer(42)).bytes)))
        assertEquals(Refusal.INVALID_CONTENT, refusal(bobOpens(sealToBob(MessageBody.Receipt(emptyList())).bytes)))
    }

    @Test
    fun `other versions and junk are refused`() {
        val envelope = Envelope.parseFrom(sealToBob().bytes)
        assertEquals(Refusal.UNSUPPORTED_VERSION, refusal(bobOpens(envelope.edit { version = 2 })))
        assertEquals(Refusal.UNREADABLE, refusal(bobOpens(byteArrayOf(1, 2, 3))))
        assertEquals(Refusal.UNREADABLE, refusal(bobOpens(ByteArray(70_000))))
    }

    @Test
    fun `ids are random and key ids differ per key`() {
        assertTrue(codec.newMessageId() != codec.newMessageId())
        assertTrue(codec.keyId(alice.keys.signPublicKey) != codec.keyId(bob.keys.signPublicKey))
        assertEquals(MessageId.SIZE, codec.newMessageId().bytes.size)
    }
}
