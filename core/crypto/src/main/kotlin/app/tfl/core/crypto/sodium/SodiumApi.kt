package app.tfl.core.crypto.sodium

import app.tfl.core.crypto.CryptoException
import com.goterl.lazysodium.Sodium
import com.goterl.lazysodium.interfaces.SecretStream
import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.ptr.IntByReference
import java.io.Closeable

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

    /** `crypto_box_seal`: encrypts [message] to an X25519 public key, anonymously; 48 bytes longer. */
    fun boxSeal(message: ByteArray, recipientPublicKey: ByteArray): ByteArray {
        require(recipientPublicKey.size == KEY_BYTES) { "X25519 public key must be 32 bytes" }
        val out = ByteArray(message.size + BOX_SEAL_BYTES)
        check(native.crypto_box_seal(out, message, message.size.toLong(), recipientPublicKey) == 0) { "Sealing failed" }
        return out
    }

    /** Opens a sealed box, or null when it isn't for this keypair or was changed. */
    fun boxSealOpen(ciphertext: ByteArray, publicKey: ByteArray, secretKey: ByteArray): ByteArray? {
        if (ciphertext.size < BOX_SEAL_BYTES || publicKey.size != KEY_BYTES || secretKey.size != KEY_BYTES) return null
        val out = ByteArray(ciphertext.size - BOX_SEAL_BYTES)
        if (native.crypto_box_seal_open(out, ciphertext, ciphertext.size.toLong(), publicKey, secretKey) != 0) {
            wipe(out)
            return null
        }
        return out
    }

    /** `sodium_pad` (ISO/IEC 7816-4): [data] padded to the next multiple of [blockSize], always growing. */
    fun pad(data: ByteArray, blockSize: Int): ByteArray {
        require(blockSize > 0) { "Invalid block size" }
        val capacity = (data.size / blockSize + 1) * blockSize
        val buffer = Memory(capacity.toLong())
        val paddedLength = sizeOut()
        try {
            buffer.write(0, data, 0, data.size)
            check(native.sodium_pad(paddedLength, buffer, data.size, blockSize, capacity) == 0) { "Padding failed" }
            return buffer.getByteArray(0, paddedLength.pointer.getLong(0).toInt())
        } finally {
            buffer.clear()
        }
    }

    /** `sodium_unpad`, or null when [padded] isn't correctly padded to [blockSize]. */
    fun unpad(padded: ByteArray, blockSize: Int): ByteArray? {
        if (blockSize <= 0 || padded.isEmpty() || padded.size % blockSize != 0) return null
        val buffer = Memory(padded.size.toLong())
        val unpaddedLength = sizeOut()
        try {
            buffer.write(0, padded, 0, padded.size)
            if (native.sodium_unpad(unpaddedLength, buffer, padded.size, blockSize) != 0) return null
            return buffer.getByteArray(0, unpaddedLength.pointer.getLong(0).toInt())
        } finally {
            buffer.clear()
        }
    }

    /**
     * An out-parameter for a C `size_t`. Lazysodium declares these as [IntByReference], which holds
     * only 4 bytes while libsodium writes 8 on 64-bit phones, so it's pointed at 8 bytes instead.
     */
    private fun sizeOut() = IntByReference().apply { pointer = Memory(Long.SIZE_BYTES.toLong()).apply { clear() } }

    /** A fresh X25519 keypair for `crypto_kx`: (public key, secret key). The caller wipes the secret. */
    fun kxKeyPair(): Pair<ByteArray, ByteArray> {
        val publicKey = ByteArray(KEY_BYTES)
        val secretKey = ByteArray(KEY_BYTES)
        check(native.crypto_kx_keypair(publicKey, secretKey) == 0) { "Key generation failed" }
        return publicKey to secretKey
    }

    /**
     * `crypto_kx` session keys for this side, as (receive key, transmit key). One side is the client
     * and the other the server; the client's transmit key is the server's receive key.
     */
    fun kxSessionKeys(client: Boolean, myPublicKey: ByteArray, mySecretKey: ByteArray, theirPublicKey: ByteArray): Pair<ByteArray, ByteArray> {
        require(myPublicKey.size == KEY_BYTES && mySecretKey.size == KEY_BYTES && theirPublicKey.size == KEY_BYTES) { "X25519 keys must be 32 bytes" }
        val rx = ByteArray(KEY_BYTES)
        val tx = ByteArray(KEY_BYTES)
        val result = if (client) {
            native.crypto_kx_client_session_keys(rx, tx, myPublicKey, mySecretKey, theirPublicKey)
        } else {
            native.crypto_kx_server_session_keys(rx, tx, myPublicKey, mySecretKey, theirPublicKey)
        }
        if (result != 0) {
            wipe(rx, tx)
            throw CryptoException("Key exchange failed") // their key is invalid (a low-order point)
        }
        return rx to tx
    }

    /** The sending half of libsodium's secretstream (XChaCha20-Poly1305): messages in order. */
    fun streamPush(key: ByteArray): StreamPush {
        require(key.size == KEY_BYTES) { "Stream key must be 32 bytes" }
        return StreamPush(key)
    }

    /** The receiving half, or null when [header] is malformed. */
    fun streamPull(key: ByteArray, header: ByteArray): StreamPull? {
        require(key.size == KEY_BYTES) { "Stream key must be 32 bytes" }
        if (header.size != STREAM_HEADER_BYTES) return null
        val state = SecretStream.State()
        if (native.crypto_secretstream_xchacha20poly1305_init_pull(state, header, key) != 0) {
            state.wipe()
            return null
        }
        return StreamPull(state)
    }

    inner class StreamPush internal constructor(key: ByteArray) : Closeable {
        private val state = SecretStream.State()

        /** Sent once, before the first message. */
        val header = ByteArray(STREAM_HEADER_BYTES)

        init {
            check(native.crypto_secretstream_xchacha20poly1305_init_push(state, header, key) == 0) { "Stream setup failed" }
        }

        fun push(message: ByteArray): ByteArray {
            val out = ByteArray(message.size + STREAM_A_BYTES)
            val result = native.crypto_secretstream_xchacha20poly1305_push(
                state, out, LongArray(1), message, message.size.toLong(), null, 0, SecretStream.TAG_MESSAGE,
            )
            check(result == 0) { "Stream encryption failed" }
            return out
        }

        override fun close() = state.wipe()
    }

    inner class StreamPull internal constructor(private val state: SecretStream.State) : Closeable {
        /** The next message, or null when it was changed, replayed or reordered. */
        fun pull(ciphertext: ByteArray): ByteArray? {
            if (ciphertext.size < STREAM_A_BYTES) return null
            val out = ByteArray(ciphertext.size - STREAM_A_BYTES)
            val result = native.crypto_secretstream_xchacha20poly1305_pull(
                state, out, LongArray(1), ByteArray(1), ciphertext, ciphertext.size.toLong(), null, 0,
            )
            if (result != 0) {
                wipe(out)
                return null
            }
            return out
        }

        override fun close() = state.wipe()
    }

    private fun SecretStream.State.wipe() {
        k?.let(::wipe)
        nonce?.let(::wipe)
        pointer.clear(size().toLong())
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
        const val BOX_SEAL_BYTES = 48
        const val STREAM_HEADER_BYTES = 24
        const val STREAM_A_BYTES = 17
        private const val GENERIC_HASH_MIN = 16
        private const val GENERIC_HASH_MAX = 64
        private const val KDF_MIN = 16
        private const val KDF_MAX = 64
        private const val ALG_ARGON2ID13 = 2
    }
}
