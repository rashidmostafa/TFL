package app.tfl.core.crypto.pairing

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeys
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.identity.DisplayNames
import app.tfl.core.model.proto.PairingPayload
import app.tfl.core.model.proto.SignedPairingCode
import app.tfl.core.model.proto.pairingPayload
import app.tfl.core.model.proto.signedPairingCode
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** A code this phone made: the QR text, and the id a friend's code quotes when it answers it. */
class IssuedCode(val text: String, val answerId: ByteArray, val createdAtMillis: Long)

/** A friend's code that passed every check. */
class ScannedCode(
    val displayName: String,
    val keys: ContactKeys,
    val createdAtMillis: Long,
    /** The id to put in our own code to answer this one. */
    val answerId: ByteArray,
    /** The [IssuedCode.answerId] of the code this one answers, if it answers one. */
    val answers: ByteArray?,
)

sealed interface PairingScan {
    class Valid(val code: ScannedCode) : PairingScan

    data class Invalid(val reason: InvalidCode) : PairingScan
}

/** Why a scanned code was refused. Nothing from a refused code is ever stored. */
sealed interface InvalidCode {
    /** Some other QR code (a website, a ticket…). */
    data object NotPairingCode : InvalidCode

    /** Starts like a TFL code but is damaged or malformed. */
    data object Unreadable : InvalidCode

    /** Made by a TFL version with a different code format. */
    data object UnsupportedVersion : InvalidCode

    /** The signature doesn't match: something in the code was changed. */
    data object BadSignature : InvalidCode

    /** Made more than 5 minutes before this phone's clock: a photo or an old screenshot. */
    data class Expired(val minutesOld: Long) : InvalidCode

    /** Made more than 5 minutes after this phone's clock: one of the two clocks is wrong. */
    data class FromTheFuture(val minutesAhead: Long) : InvalidCode

    /** The name breaks the display-name rules (see [DisplayNames]). */
    data object BadName : InvalidCode

    /** This phone's own code, seen in a mirror or a screenshot. */
    data object OwnCode : InvalidCode
}

/**
 * The QR codes friends scan in person. A code carries public information only: name, public keys,
 * time and a random nonce, signed with the Ed25519 identity key. The signed bytes are exactly the
 * payload bytes in the QR (never re-encoded), behind a domain label so a pairing signature can
 * never pass for any other TFL signature. See docs/SECURITY_DESIGN.md.
 */
@Singleton
class PairingCodes @Inject constructor(
    private val sodium: SodiumApi,
    private val fingerprints: Fingerprints,
) {

    /** A new code for [keys], valid for 5 minutes from [nowMillis]; it answers [answers] if given. */
    fun create(keys: IdentityKeys, displayName: String, nowMillis: Long, answers: ByteArray? = null): IssuedCode {
        require(DisplayNames.isValid(displayName)) { "Invalid display name" }
        require(answers == null || answers.size == ANSWER_BYTES) { "An answer is a $ANSWER_BYTES-byte code id" }
        val payload = pairingPayload {
            version = VERSION
            this.displayName = displayName
            signPublicKey = ByteString.copyFrom(keys.signPublicKey)
            kexPublicKey = ByteString.copyFrom(keys.kexPublicKey)
            createdAtMillis = nowMillis
            nonce = ByteString.copyFrom(sodium.randomBytes(NONCE_BYTES))
            if (answers != null) this.answers = ByteString.copyFrom(answers)
        }.toByteArray()
        val signature = sodium.signDetached(signedMessage(payload), keys.signSecretKey)
        val code = signedPairingCode {
            this.payload = ByteString.copyFrom(payload)
            this.signature = ByteString.copyFrom(signature)
        }
        return IssuedCode(PREFIX + ENCODER.encodeToString(code.toByteArray()), answerId(payload), nowMillis)
    }

    /**
     * Checks a scanned QR text, in this order: TFL prefix, size and structure, version, signature,
     * age (within 5 minutes of [nowMillis] either way), name, and that it isn't [ownSignPublicKey]'s.
     */
    fun parse(text: String, nowMillis: Long, ownSignPublicKey: ByteArray?): PairingScan {
        if (!text.startsWith(PREFIX)) return invalid(InvalidCode.NotPairingCode)
        val bytes = try {
            DECODER.decode(text.substring(PREFIX.length).trim())
        } catch (e: IllegalArgumentException) {
            return invalid(InvalidCode.Unreadable)
        }
        if (bytes.size > MAX_CODE_BYTES) return invalid(InvalidCode.Unreadable)

        val code: SignedPairingCode
        val payload: PairingPayload
        try {
            code = SignedPairingCode.parseFrom(bytes)
            payload = PairingPayload.parseFrom(code.payload)
        } catch (e: InvalidProtocolBufferException) {
            return invalid(InvalidCode.Unreadable)
        }
        val payloadBytes = code.payload.toByteArray()
        val signKey = payload.signPublicKey.toByteArray()
        val kexKey = payload.kexPublicKey.toByteArray()
        val answers = payload.answers.toByteArray()
        val wellFormed = signKey.size == SodiumApi.ED25519_PUBLIC_BYTES &&
            kexKey.size == SodiumApi.KEY_BYTES &&
            payload.nonce.size() == NONCE_BYTES &&
            (answers.isEmpty() || answers.size == ANSWER_BYTES) &&
            code.signature.size() == SodiumApi.ED25519_SIGNATURE_BYTES
        if (!wellFormed) return invalid(InvalidCode.Unreadable)
        if (payload.version != VERSION) return invalid(InvalidCode.UnsupportedVersion)
        if (!sodium.verifyDetached(code.signature.toByteArray(), signedMessage(payloadBytes), signKey)) {
            return invalid(InvalidCode.BadSignature)
        }

        val age = nowMillis - payload.createdAtMillis
        if (age > VALIDITY_MILLIS) return invalid(InvalidCode.Expired(age / MINUTE_MILLIS))
        if (-age > VALIDITY_MILLIS) return invalid(InvalidCode.FromTheFuture(-age / MINUTE_MILLIS))
        if (!DisplayNames.isValid(payload.displayName)) return invalid(InvalidCode.BadName)
        if (ownSignPublicKey != null && sodium.constantTimeEquals(signKey, ownSignPublicKey)) return invalid(InvalidCode.OwnCode)

        return PairingScan.Valid(
            ScannedCode(
                displayName = payload.displayName,
                keys = ContactKeys(signKey, kexKey, fingerprints.of(signKey)),
                createdAtMillis = payload.createdAtMillis,
                answerId = answerId(payloadBytes),
                answers = answers.takeIf { it.isNotEmpty() },
            ),
        )
    }

    private fun signedMessage(payload: ByteArray): ByteArray = SIGNATURE_CONTEXT + payload

    /** The id of a code: BLAKE2b-128 of its payload bytes, which include the owner's key and nonce. */
    private fun answerId(payload: ByteArray): ByteArray = sodium.genericHash(ANSWER_BYTES, ANSWER_CONTEXT + payload)

    private fun invalid(reason: InvalidCode) = PairingScan.Invalid(reason)

    companion object {
        const val PREFIX = "TFL-PAIR1:"
        const val VERSION = 1
        const val VALIDITY_MILLIS = 5 * 60_000L
        const val REFRESH_MILLIS = 60_000L
        const val NONCE_BYTES = 16
        const val ANSWER_BYTES = 16
        private const val MAX_CODE_BYTES = 1024
        private const val MINUTE_MILLIS = 60_000L
        private val SIGNATURE_CONTEXT = "TFL-pairing-v1".encodeToByteArray() + 0.toByte()
        private val ANSWER_CONTEXT = "TFL-pairing-answer-v1".encodeToByteArray() + 0.toByte()
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }
}
