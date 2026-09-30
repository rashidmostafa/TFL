package app.tfl.core.crypto.sodium

import app.tfl.core.crypto.hexToBytes
import app.tfl.core.crypto.toHex
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningTest {

    private val sodium = JvmSodium.api

    /** RFC 8032, section 7.1, test 1: the empty message. */
    private val rfcSeed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".hexToBytes()
    private val rfcPublicKey = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".hexToBytes()
    private val rfcSignature = (
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555" +
            "fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"
        ).hexToBytes()

    @Test
    fun `signs the RFC 8032 test vector exactly`() {
        val (publicKey, secretKey) = sodium.ed25519KeyPair(rfcSeed)
        assertEquals(rfcPublicKey.toHex(), publicKey.toHex())
        assertEquals(rfcSignature.toHex(), sodium.signDetached(ByteArray(0), secretKey).toHex())
        assertTrue(sodium.verifyDetached(rfcSignature, ByteArray(0), rfcPublicKey))
    }

    @Test
    fun `a signature fails for any other message, key or tampered signature`() {
        val (publicKey, secretKey) = sodium.ed25519KeyPair(sodium.randomBytes(32))
        val (otherPublicKey, _) = sodium.ed25519KeyPair(sodium.randomBytes(32))
        val message = "meet at the bridge".encodeToByteArray()
        val signature = sodium.signDetached(message, secretKey)

        assertTrue(sodium.verifyDetached(signature, message, publicKey))
        assertFalse(sodium.verifyDetached(signature, "meet at the bridgf".encodeToByteArray(), publicKey))
        assertFalse(sodium.verifyDetached(signature, message, otherPublicKey))
        for (index in signature.indices) {
            val tampered = signature.copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }
            assertFalse("flipped signature byte $index", sodium.verifyDetached(tampered, message, publicKey))
        }
    }

    @Test
    fun `wrong sizes are rejected rather than crashing`() {
        val (publicKey, secretKey) = sodium.ed25519KeyPair(sodium.randomBytes(32))
        val signature = sodium.signDetached(byteArrayOf(1), secretKey)
        assertFalse(sodium.verifyDetached(signature.copyOf(63), byteArrayOf(1), publicKey))
        assertFalse(sodium.verifyDetached(signature, byteArrayOf(1), publicKey.copyOf(31)))
        assertFalse(sodium.verifyDetached(signature, byteArrayOf(1), ByteArray(32)))
    }
}
