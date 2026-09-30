package app.tfl.core.crypto.identity

import app.tfl.core.crypto.hexToBytes
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintsTest {

    private val fingerprints = Fingerprints(JvmSodium.api)

    /** Expected value from Python: `blake2b(b"TFL-fingerprint-v1" + pk, digest_size=16).hexdigest().upper()`. */
    @Test
    fun `fingerprint matches an independent BLAKE2b computation`() {
        val rfc8032PublicKey = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".hexToBytes()
        assertEquals("F162BE7D2746777DB5A0FF5D6329EC0E", fingerprints.of(rfc8032PublicKey))
    }

    @Test
    fun `fingerprint is 32 uppercase hex characters`() {
        val value = fingerprints.of(ByteArray(32) { 7 })
        assertEquals(32, value.length)
        assertTrue(value.all { it in '0'..'9' || it in 'A'..'F' })
        assertEquals(8, value.chunked(4).size)
    }

    @Test
    fun `different keys have different fingerprints`() {
        assertNotEquals(fingerprints.of(ByteArray(32) { 1 }), fingerprints.of(ByteArray(32) { 2 }))
    }

    @Test
    fun `only Ed25519 public keys are accepted`() {
        assertThrows(IllegalArgumentException::class.java) { fingerprints.of(ByteArray(31)) }
    }
}
