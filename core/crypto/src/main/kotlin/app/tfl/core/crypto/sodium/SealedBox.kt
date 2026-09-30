package app.tfl.core.crypto.sodium

import app.tfl.core.crypto.AuthenticationException
import javax.inject.Inject

/** What a sealed blob contains. The label is authenticated, so a blob can't be passed off as another kind. */
enum class BlobLabel(internal val value: String) {
    UNLOCK_KEY_BY_PIN("tfl/v1/uk-pin"),
    DATABASE_KEY("tfl/v1/dbkey"),
    SEED("tfl/v1/seed"),
}

/**
 * Small secrets sealed with XChaCha20-Poly1305 under a random 24-byte nonce.
 *
 * Format: `version (1) ‖ nonce (24) ‖ ciphertext ‖ tag (16)`. The version byte and label are the
 * additional authenticated data.
 */
class SealedBox @Inject constructor(private val sodium: SodiumApi) {

    fun seal(key: ByteArray, plaintext: ByteArray, label: BlobLabel): ByteArray {
        val nonce = sodium.randomBytes(SodiumApi.AEAD_NONCE_BYTES)
        val ciphertext = sodium.aeadEncrypt(plaintext, additionalData(VERSION, label), nonce, key)
        return byteArrayOf(VERSION) + nonce + ciphertext
    }

    /** @throws AuthenticationException if the blob was modified or the key or label is wrong. */
    fun open(key: ByteArray, blob: ByteArray, label: BlobLabel): ByteArray {
        if (blob.size < HEADER_BYTES + SodiumApi.AEAD_TAG_BYTES || blob[0] != VERSION) {
            throw AuthenticationException("Unsupported or truncated sealed blob")
        }
        val nonce = blob.copyOfRange(1, HEADER_BYTES)
        val ciphertext = blob.copyOfRange(HEADER_BYTES, blob.size)
        return sodium.aeadDecrypt(ciphertext, additionalData(blob[0], label), nonce, key)
            ?: throw AuthenticationException("Sealed blob failed authentication")
    }

    private fun additionalData(version: Byte, label: BlobLabel): ByteArray =
        byteArrayOf(version) + label.value.encodeToByteArray()

    private companion object {
        const val VERSION: Byte = 1
        const val HEADER_BYTES = 1 + SodiumApi.AEAD_NONCE_BYTES
    }
}
