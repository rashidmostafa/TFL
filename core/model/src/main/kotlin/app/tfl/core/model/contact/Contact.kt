package app.tfl.core.model.contact

/** How sure this phone is that it holds a friend's real keys. */
enum class Verification { UNVERIFIED, VERIFIED }

/** How a friend was verified. */
enum class VerificationMethod {
    /** Both phones scanned each other's pairing codes, in person, within 5 minutes. */
    IN_PERSON_PAIRING,

    /** Their safety-number code was scanned and matched. */
    SAFETY_NUMBER_QR,

    /** The two of you read the safety number aloud and marked it verified. */
    SAFETY_NUMBER_MANUAL,
}

/** How a friend's key reached this phone. Later phases add the transports. */
enum class KeySource {
    IN_PERSON_SCAN,

    /** Debug builds only: "simulate key change". */
    DEBUG_SIMULATION,
}

/** A friend's public keys, as their pairing code presented them. */
class ContactKeys(
    val signPublicKey: ByteArray,
    val kexPublicKey: ByteArray,
    /** 32 uppercase hex characters, as shown to people. */
    val fingerprintHex: String,
) {
    fun sameAs(other: ContactKeys): Boolean =
        signPublicKey.contentEquals(other.signPublicKey) && kexPublicKey.contentEquals(other.kexPublicKey)

    val fingerprintGroups: List<String> get() = fingerprintHex.chunked(4)
}

class Contact(
    val id: Long,
    /** The name in their pairing code. */
    val displayName: String,
    /** Your own name for them, if you set one; only on this phone. */
    val nickname: String?,
    val keys: ContactKeys,
    val keySource: KeySource,
    val verification: Verification,
    val verifiedBy: VerificationMethod?,
    val verifiedAtMillis: Long?,
    val firstSeenAtMillis: Long,
    val lastPairedAtMillis: Long,
    /** When their identity key changed, while that change hasn't been verified yet. */
    val keyChangedAtMillis: Long?,
    val blocked: Boolean,
) {
    val name: String get() = nickname?.takeIf { it.isNotBlank() } ?: displayName

    val isVerified: Boolean get() = verification == Verification.VERIFIED

    val keyChanged: Boolean get() = keyChangedAtMillis != null

    /**
     * Whether messages may go to this friend: never while blocked, and never after a key change
     * until the new key is verified. Every transport checks this from Phase 3.
     */
    val canSend: Boolean get() = !blocked && !(keyChanged && !isVerified)
}

/** A key a friend used before their current one. */
class PreviousKey(
    val keys: ContactKeys,
    val firstSeenAtMillis: Long,
    val replacedAtMillis: Long,
    /** How the key that replaced it arrived. */
    val replacedBy: KeySource,
)
