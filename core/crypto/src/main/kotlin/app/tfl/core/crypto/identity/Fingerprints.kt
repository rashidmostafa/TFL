package app.tfl.core.crypto.identity

import app.tfl.core.crypto.sodium.SodiumApi
import javax.inject.Inject

/**
 * The fingerprint friends compare in person: BLAKE2b-128 of a domain label and the Ed25519 public
 * key, as 32 uppercase hex characters (shown in 8 groups of 4). 128 bits keep it short enough to read
 * aloud while making a key with a matching fingerprint infeasible to create.
 */
class Fingerprints @Inject constructor(private val sodium: SodiumApi) {

    fun of(signPublicKey: ByteArray): String {
        require(signPublicKey.size == SodiumApi.ED25519_PUBLIC_BYTES) { "Not an Ed25519 public key" }
        val digest = sodium.genericHash(FINGERPRINT_BYTES, DOMAIN + signPublicKey)
        return digest.joinToString("") { "%02X".format(it) }
    }

    private companion object {
        const val FINGERPRINT_BYTES = 16
        val DOMAIN = "TFL-fingerprint-v1".encodeToByteArray()
    }
}
