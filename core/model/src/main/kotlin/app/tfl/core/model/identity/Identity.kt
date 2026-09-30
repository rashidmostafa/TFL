package app.tfl.core.model.identity

/**
 * This phone's identity, as stored in the database. Only public keys are here: the private keys
 * are derived from the encrypted master seed when needed and never stored.
 */
class Identity(
    val displayName: String,
    /** 128-bit fingerprint of the Ed25519 public key, 32 uppercase hex characters. */
    val fingerprintHex: String,
    val signPublicKey: ByteArray,
    val kexPublicKey: ByteArray,
    val createdAtMillis: Long,
) {
    /** Groups of four hex characters, as friends compare them aloud. */
    val fingerprintGroups: List<String> get() = fingerprintHex.chunked(4)

    override fun equals(other: Any?): Boolean =
        other is Identity &&
            displayName == other.displayName &&
            fingerprintHex == other.fingerprintHex &&
            signPublicKey.contentEquals(other.signPublicKey) &&
            kexPublicKey.contentEquals(other.kexPublicKey) &&
            createdAtMillis == other.createdAtMillis

    override fun hashCode(): Int = fingerprintHex.hashCode()
}

/** Where a profile is in onboarding. Steps after the commit point are saved and resumed after unlock. */
enum class OnboardingStep {
    RECOVERY_PHRASE,
    PERMISSIONS,
    NETWORK,
    DISGUISE,
    DONE,
}
