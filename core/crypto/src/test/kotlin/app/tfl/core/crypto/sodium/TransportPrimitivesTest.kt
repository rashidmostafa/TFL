package app.tfl.core.crypto.sodium

import app.tfl.core.crypto.CryptoException
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The libsodium calls the envelope and the link are built on. */
class TransportPrimitivesTest {

    private val sodium = JvmSodium.api

    @Test
    fun `a sealed box opens only with the recipient's keypair, and not after any change`() {
        val (publicKey, secretKey) = sodium.kxKeyPair()
        val (otherPublic, otherSecret) = sodium.kxKeyPair()
        val message = "meet at the water tower".encodeToByteArray()
        val sealed = sodium.boxSeal(message, publicKey)
        assertEquals(message.size + SodiumApi.BOX_SEAL_BYTES, sealed.size)
        assertArrayEquals(message, sodium.boxSealOpen(sealed, publicKey, secretKey))
        assertNull(sodium.boxSealOpen(sealed, otherPublic, otherSecret))
        for (i in sealed.indices) {
            val changed = sealed.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertNull("byte $i", sodium.boxSealOpen(changed, publicKey, secretKey))
        }
    }

    @Test
    fun `padding reaches the next multiple of the block, always growing, and comes off exactly`() {
        for ((length, block) in listOf(0 to 256, 1 to 256, 255 to 256, 256 to 256, 1_000 to 1_024, 16_383 to 16_384)) {
            val data = sodium.randomBytes(length)
            val padded = sodium.pad(data, block)
            assertEquals("length $length", (length / block + 1) * block, padded.size)
            assertArrayEquals(data, sodium.unpad(padded, block))
        }
    }

    @Test
    fun `bad padding is refused`() {
        assertNull(sodium.unpad(ByteArray(256), 256)) // no 0x80 marker
        assertNull(sodium.unpad(ByteArray(100), 256)) // not a whole block
        assertNull(sodium.unpad(ByteArray(0), 256))
    }

    @Test
    fun `key exchange gives the client and the server matching keys`() {
        val (clientPublic, clientSecret) = sodium.kxKeyPair()
        val (serverPublic, serverSecret) = sodium.kxKeyPair()
        val (clientRx, clientTx) = sodium.kxSessionKeys(true, clientPublic, clientSecret, serverPublic)
        val (serverRx, serverTx) = sodium.kxSessionKeys(false, serverPublic, serverSecret, clientPublic)
        assertArrayEquals(clientTx, serverRx)
        assertArrayEquals(clientRx, serverTx)
    }

    @Test
    fun `key exchange with a low-order key is refused`() {
        val (publicKey, secretKey) = sodium.kxKeyPair()
        assertThrows(CryptoException::class.java) { sodium.kxSessionKeys(true, publicKey, secretKey, ByteArray(32)) }
    }

    @Test
    fun `the stream delivers in order and refuses changed, replayed or reordered frames`() {
        val key = sodium.randomBytes(32)
        val push = sodium.streamPush(key)
        val first = push.push("one".encodeToByteArray())
        val second = push.push("two".encodeToByteArray())

        val pull = checkNotNull(sodium.streamPull(key, push.header))
        assertArrayEquals("one".encodeToByteArray(), pull.pull(first))
        assertNull("replayed", pull.pull(first))

        val reordered = checkNotNull(sodium.streamPull(key, push.header))
        assertNull("out of order", reordered.pull(second))

        val tampered = checkNotNull(sodium.streamPull(key, push.header))
        assertArrayEquals("one".encodeToByteArray(), tampered.pull(first))
        assertNull("changed", tampered.pull(second.copyOf().also { it[5] = (it[5].toInt() xor 1).toByte() }))

        val wrongKey = checkNotNull(sodium.streamPull(sodium.randomBytes(32), push.header))
        assertNull(wrongKey.pull(first))
    }
}
