package app.tfl.core.crypto.identity

import app.tfl.core.crypto.sodium.KdfContext
import app.tfl.core.crypto.sodium.SodiumApi
import javax.inject.Inject

/**
 * Keys derived from the master seed. The same seed always gives the same keys, which is what lets a
 * recovery phrase restore an identity. [close] wipes the secret parts.
 */
class IdentityKeys internal constructor(
    private val sodium: SodiumApi,
    /** Ed25519 public key: signs everything TFL sends. */
    val signPublicKey: ByteArray,
    /** Ed25519 secret key (libsodium's 64-byte form). */
    val signSecretKey: ByteArray,
    /** X25519 public key for key agreement. */
    val kexPublicKey: ByteArray,
    val kexSecretKey: ByteArray,
    /** Encrypts backups stored with a friend (Phase 7). */
    val backupKey: ByteArray,
) : AutoCloseable {
    override fun close() = sodium.wipe(signSecretKey, kexSecretKey, backupKey)
}

class IdentityKeyDerivation @Inject constructor(private val sodium: SodiumApi) {

    /** Derivation scheme stored alongside the identity, so it can change later without breaking old ones. */
    val version: Int get() = DERIVATION_VERSION

    fun derive(seed: ByteArray): IdentityKeys {
        require(seed.size == SodiumApi.KEY_BYTES) { "Seed must be 32 bytes" }
        val signSeed = sodium.deriveKey(seed, DERIVATION_VERSION.toLong(), KdfContext.IDENTITY_SIGN)
        val kexSecret = sodium.deriveKey(seed, DERIVATION_VERSION.toLong(), KdfContext.IDENTITY_KEX)
        val backupKey = sodium.deriveKey(seed, DERIVATION_VERSION.toLong(), KdfContext.BACKUP)
        try {
            val (signPublic, signSecret) = sodium.ed25519KeyPair(signSeed)
            return IdentityKeys(
                sodium = sodium,
                signPublicKey = signPublic,
                signSecretKey = signSecret,
                kexPublicKey = sodium.x25519PublicKey(kexSecret),
                kexSecretKey = kexSecret,
                backupKey = backupKey,
            )
        } finally {
            sodium.wipe(signSeed)
        }
    }

    private companion object {
        const val DERIVATION_VERSION = 1
    }
}
