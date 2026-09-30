package app.tfl.core.crypto.sodium

import app.tfl.core.crypto.AuthenticationException
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SealedBoxTest {

    private val sodium = JvmSodium.api
    private val box = SealedBox(sodium)
    private val key = sodium.randomBytes(32)
    private val secret = "the unlock key".encodeToByteArray()

    @Test
    fun `sealed data opens with the same key and label`() {
        val blob = box.seal(key, secret, BlobLabel.SEED)
        assertArrayEquals(secret, box.open(key, blob, BlobLabel.SEED))
    }

    @Test
    fun `each seal uses a fresh nonce`() {
        assertFalse(box.seal(key, secret, BlobLabel.SEED).contentEquals(box.seal(key, secret, BlobLabel.SEED)))
    }

    @Test
    fun `flipping any byte is detected`() {
        val blob = box.seal(key, secret, BlobLabel.SEED)
        // version byte, nonce, ciphertext and tag
        for (index in listOf(0, 1, 24, 25, blob.size - 17, blob.size - 1)) {
            val tampered = blob.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertThrows("byte $index", AuthenticationException::class.java) { box.open(key, tampered, BlobLabel.SEED) }
        }
    }

    @Test
    fun `wrong key is rejected`() {
        val blob = box.seal(key, secret, BlobLabel.SEED)
        assertThrows(AuthenticationException::class.java) { box.open(sodium.randomBytes(32), blob, BlobLabel.SEED) }
    }

    @Test
    fun `a blob can't be opened as another kind`() {
        val blob = box.seal(key, secret, BlobLabel.DATABASE_KEY)
        assertThrows(AuthenticationException::class.java) { box.open(key, blob, BlobLabel.SEED) }
    }

    @Test
    fun `truncated blobs are rejected`() {
        val blob = box.seal(key, secret, BlobLabel.SEED)
        assertThrows(AuthenticationException::class.java) { box.open(key, blob.copyOf(20), BlobLabel.SEED) }
        assertThrows(AuthenticationException::class.java) { box.open(key, blob.copyOf(blob.size - 1), BlobLabel.SEED) }
    }
}
