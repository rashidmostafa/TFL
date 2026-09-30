package app.tfl.core.crypto.sodium

import com.goterl.lazysodium.Sodium
import com.sun.jna.NativeLong

/**
 * The libsodium operations TFL uses, over Lazysodium's raw JNA bindings.
 *
 * Everything works on [ByteArray]s so secrets never become immutable Strings. Callers own the
 * arrays they get back and should [wipe] secret ones as soon as they are done with them.
 */
class SodiumApi(private val native: Sodium) {

    init {
        // 0 = initialised now, 1 = already initialised, -1 = failure.
        check(native.sodium_init() >= 0) { "libsodium failed to initialise" }
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { native.randombytes_buf(it, size) }

    /** Zeroes each array in a way the compiler can't optimise away. */
    fun wipe(vararg arrays: ByteArray?) {
        for (array in arrays) {
            if (array != null && array.isNotEmpty()) native.sodium_memzero(array, array.size)
        }
    }

    /** Constant-time comparison. Only the lengths, which are never secret here, are compared early. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        a.size == b.size && native.sodium_memcmp(a, b, a.size) == 0

    fun sha256(input: ByteArray): ByteArray {
        val out = ByteArray(SHA256_BYTES)
        check(native.crypto_hash_sha256(out, input, input.size.toLong()) == 0) { "SHA-256 failed" }
        return out
    }

    /** BLAKE2b (`crypto_generichash`), optionally keyed with 16–64 bytes. */
    fun genericHash(outputLength: Int, input: ByteArray, key: ByteArray? = null): ByteArray {
        require(outputLength in GENERIC_HASH_MIN..GENERIC_HASH_MAX) { "Invalid BLAKE2b output length" }
        require(key == null || key.size in GENERIC_HASH_MIN..GENERIC_HASH_MAX) { "Invalid BLAKE2b key length" }
        val out = ByteArray(outputLength)
        val result = native.crypto_generichash(out, outputLength, input, input.size.toLong(), key, key?.size ?: 0)
        check(result == 0) { "BLAKE2b failed" }
        return out
    }

    /** `crypto_kdf_derive_from_key`: an independent subkey of [masterKey] per (context, id). */
    fun deriveKey(masterKey: ByteArray, subkeyId: Long, context: KdfContext, length: Int = KEY_BYTES): ByteArray {
        require(masterKey.size == KEY_BYTES) { "KDF master key must be 32 bytes" }
        require(length in KDF_MIN..KDF_MAX) { "Invalid KDF output length" }
        val out = ByteArray(length)
        val result = native.crypto_kdf_derive_from_key(out, length, subkeyId, context.bytes(), masterKey)
        check(result == 0) { "Key derivation failed" }
        return out
    }

    /** Ed25519 keypair from a 32-byte seed: (public key 32 B, secret key 64 B). */
    fun ed25519KeyPair(seed: ByteArray): Pair<ByteArray, ByteArray> {
        require(seed.size == KEY_BYTES) { "Ed25519 seed must be 32 bytes" }
        val publicKey = ByteArray(ED25519_PUBLIC_BYTES)
        val secretKey = ByteArray(ED25519_SECRET_BYTES)
        check(native.crypto_sign_seed_keypair(publicKey, secretKey, seed) == 0) { "Ed25519 key generation failed" }
        return publicKey to secretKey
    }

    /** Ed25519 detached signature (64 bytes) over [message] with a 64-byte secret key. */
    fun signDetached(message: ByteArray, secretKey: ByteArray): ByteArray {
        require(secretKey.size == ED25519_SECRET_BYTES) { "Ed25519 secret key must be 64 bytes" }
        val signature = ByteArray(ED25519_SIGNATURE_BYTES)
        check(native.crypto_sign_detached(signature, null, message, message.size.toLong(), secretKey) == 0) { "Ed25519 signing failed" }
        return signature
    }

    /**
     * Whether [signature] is a valid Ed25519 signature of [message] by [publicKey]. Wrong sizes and
     * keys libsodium rejects (such as small-order points) simply fail.
     */
    fun verifyDetached(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != ED25519_SIGNATURE_BYTES || publicKey.size != ED25519_PUBLIC_BYTES) return false
        return native.crypto_sign_verify_detached(signature, message, message.size.toLong(), publicKey) == 0
    }

    /** X25519 public key for a 32-byte secret scalar (libsodium clamps it). */
    fun x25519PublicKey(secretKey: ByteArray): ByteArray {
        require(secretKey.size == KEY_BYTES) { "X25519 secret key must be 32 bytes" }
        val publicKey = ByteArray(KEY_BYTES)
        check(native.crypto_scalarmult_base(publicKey, secretKey) == 0) { "X25519 public key derivation failed" }
        return publicKey
    }

    /** XChaCha20-Poly1305 (IETF) encryption. Returns ciphertext followed by the 16-byte tag. */
    fun aeadEncrypt(plaintext: ByteArray, additionalData: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        require(nonce.size == AEAD_NONCE_BYTES && key.size == KEY_BYTES) { "Invalid AEAD nonce or key" }
        val out = ByteArray(plaintext.size + AEAD_TAG_BYTES)
        val result = native.crypto_aead_xchacha20poly1305_ietf_encrypt(
            out, LongArray(1), plaintext, plaintext.size.toLong(),
            additionalData, additionalData.size.toLong(), null, nonce, key,
        )
        check(result == 0) { "Encryption failed" }
        return out
    }

    /** XChaCha20-Poly1305 (IETF) decryption, or null when authentication fails. */
    fun aeadDecrypt(ciphertext: ByteArray, additionalData: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray? {
        if (ciphertext.size < AEAD_TAG_BYTES || nonce.size != AEAD_NONCE_BYTES || key.size != KEY_BYTES) return null
        val out = ByteArray(ciphertext.size - AEAD_TAG_BYTES)
        val result = native.crypto_aead_xchacha20poly1305_ietf_decrypt(
            out, LongArray(1), null, ciphertext, ciphertext.size.toLong(),
            additionalData, additionalData.size.toLong(), nonce, key,
        )
        if (result != 0) {
            wipe(out)
            return null
        }
        return out
    }

    /** Argon2id (v1.3). Returns null when libsodium can't run it, typically because memory ran out. */
    fun argon2id(outputLength: Int, password: ByteArray, salt: ByteArray, opsLimit: Long, memLimitBytes: Long): ByteArray? {
        require(salt.size == ARGON2_SALT_BYTES) { "Argon2id salt must be 16 bytes" }
        val out = ByteArray(outputLength)
        val result = native.crypto_pwhash(
            out, outputLength.toLong(), password, password.size.toLong(), salt,
            opsLimit, NativeLong(memLimitBytes), ALG_ARGON2ID13,
        )
        if (result != 0) {
            wipe(out)
            return null
        }
        return out
    }

    companion object {
        const val KEY_BYTES = 32
        const val SHA256_BYTES = 32
        const val ED25519_PUBLIC_BYTES = 32
        const val ED25519_SECRET_BYTES = 64
        const val ED25519_SIGNATURE_BYTES = 64
        const val AEAD_NONCE_BYTES = 24
        const val AEAD_TAG_BYTES = 16
        const val ARGON2_SALT_BYTES = 16
        private const val GENERIC_HASH_MIN = 16
        private const val GENERIC_HASH_MAX = 64
        private const val KDF_MIN = 16
        private const val KDF_MAX = 64
        private const val ALG_ARGON2ID13 = 2
    }
}
