package app.tfl.core.crypto.identity

import app.tfl.core.crypto.hexToBytes
import app.tfl.core.crypto.sodium.KdfContext
import app.tfl.core.crypto.toHex
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityKeysTest {

    private val sodium = JvmSodium.api
    private val derivation = IdentityKeyDerivation(sodium)
    private val seed = ByteArray(32) { it.toByte() } // 00 01 02 … 1f

    @Test
    fun `same seed gives the same identity`() {
        val first = derivation.derive(seed)
        val second = derivation.derive(seed.copyOf())
        assertArrayEquals(first.signPublicKey, second.signPublicKey)
        assertArrayEquals(first.signSecretKey, second.signSecretKey)
        assertArrayEquals(first.kexPublicKey, second.kexPublicKey)
        assertArrayEquals(first.kexSecretKey, second.kexSecretKey)
        assertArrayEquals(first.backupKey, second.backupKey)
    }

    @Test
    fun `different seeds give unrelated identities`() {
        val a = derivation.derive(seed)
        val b = derivation.derive(sodium.randomBytes(32))
        assertFalse(a.signPublicKey.contentEquals(b.signPublicKey))
        assertFalse(a.kexPublicKey.contentEquals(b.kexPublicKey))
        assertFalse(a.backupKey.contentEquals(b.backupKey))
    }

    /**
     * Expected subkeys computed independently with Python's hashlib (libsodium's crypto_kdf is
     * BLAKE2b keyed with the master key, salt = LE64(id) ‖ 0⁸, personal = context ‖ 0⁸):
     * `blake2b(b'', digest_size=32, key=seed, salt=pack('<Q', 1) + bytes(8), person=ctx + bytes(8))`.
     */
    @Test
    fun `key derivation matches an independent BLAKE2b computation`() {
        assertEquals(
            "f045c77dcc2664f2de56bb9d2d078e9216465428c199fe6bb03efddb73358172",
            sodium.deriveKey(seed, 1, KdfContext.IDENTITY_SIGN).toHex(),
        )
        assertEquals(
            "680f68aa5cecabad240d9d4caf5849ed479d74eb90285be15796f2b5bb30df3d",
            sodium.deriveKey(seed, 1, KdfContext.IDENTITY_KEX).toHex(),
        )
        assertEquals(
            "52b9384aa5b1f9747d17669c2534331c0a4013d00e047df57d0c5d6cad4f2fab",
            sodium.deriveKey(seed, 1, KdfContext.BACKUP).toHex(),
        )
    }

    @Test
    fun `each purpose uses its own derived key`() {
        derivation.derive(seed).use { keys ->
            assertEquals("52b9384aa5b1f9747d17669c2534331c0a4013d00e047df57d0c5d6cad4f2fab", keys.backupKey.toHex())
            assertEquals("680f68aa5cecabad240d9d4caf5849ed479d74eb90285be15796f2b5bb30df3d", keys.kexSecretKey.toHex())
            // libsodium's Ed25519 secret key is seed ‖ public key; the seed half is the signing subkey.
            assertEquals(
                "f045c77dcc2664f2de56bb9d2d078e9216465428c199fe6bb03efddb73358172",
                keys.signSecretKey.copyOf(32).toHex(),
            )
            assertArrayEquals(keys.signPublicKey, keys.signSecretKey.copyOfRange(32, 64))
        }
    }

    /** RFC 8032 §7.1, test 1. */
    @Test
    fun `ed25519 matches RFC 8032`() {
        val (publicKey, _) = sodium.ed25519KeyPair("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".hexToBytes())
        assertEquals("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a", publicKey.toHex())
    }

    /** RFC 7748 §6.1 (Alice). */
    @Test
    fun `x25519 matches RFC 7748`() {
        val publicKey = sodium.x25519PublicKey("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToBytes())
        assertEquals("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a", publicKey.toHex())
    }

    @Test
    fun `closing wipes the secret keys but not the public ones`() {
        val keys = derivation.derive(seed)
        val publicKey = keys.signPublicKey.copyOf()
        keys.close()
        assertTrue(keys.signSecretKey.all { it == 0.toByte() })
        assertTrue(keys.kexSecretKey.all { it == 0.toByte() })
        assertTrue(keys.backupKey.all { it == 0.toByte() })
        assertArrayEquals(publicKey, keys.signPublicKey)
    }
}
