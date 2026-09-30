package app.tfl.core.crypto.lock

import app.tfl.core.testing.crypto.JvmSodium
import com.goterl.lazysodium.interfaces.PwHash
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KdfParamsTest {

    private val sodium = JvmSodium.api
    private val hasher = Argon2idHasher(sodium)

    @Test
    fun `moderate matches libsodium's constants`() {
        assertEquals(PwHash.OPSLIMIT_MODERATE, KdfParams.MODERATE.opsLimit)
        assertEquals(PwHash.MEMLIMIT_MODERATE.toLong(), KdfParams.MODERATE.memLimitBytes)
        assertEquals(256L, KdfParams.MODERATE.memLimitMebibytes)
    }

    @Test
    fun `argon2id at moderate cost is deterministic and salted`() {
        val pin = "246810".encodeToByteArray()
        val salt = ByteArray(16) { 1 }
        val first = hasher.derive(pin, salt, KdfParams.MODERATE)
        assertArrayEquals(first, hasher.derive(pin, salt, KdfParams.MODERATE))
        assertFalse(first.contentEquals(hasher.derive(pin, ByteArray(16) { 2 }, KdfParams.MODERATE)))
    }
}
