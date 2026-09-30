package app.tfl.core.crypto.pairing

import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.proto.SafetyNumberCode
import app.tfl.core.model.proto.safetyNumberCode
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The number two friends compare to check they hold each other's real keys. Both phones compute it
 * from the same two keys, so it's identical on both; any other key on either side changes it.
 */
class SafetyNumber internal constructor(internal val digest: ByteArray) {

    /** 12 groups of 5 digits: each from 5 bytes of the digest, taken mod 100,000. */
    val groups: List<String> = List(GROUPS) { group ->
        var value = 0L
        for (index in group * BYTES_PER_GROUP until (group + 1) * BYTES_PER_GROUP) {
            value = (value shl 8) or (digest[index].toLong() and 0xFF)
        }
        (value % GROUP_MODULUS).toString().padStart(DIGITS_PER_GROUP, '0')
    }

    override fun equals(other: Any?): Boolean = other is SafetyNumber && digest.contentEquals(other.digest)

    override fun hashCode(): Int = digest.contentHashCode()

    internal companion object {
        const val GROUPS = 12
        const val BYTES_PER_GROUP = 5
        const val DIGITS_PER_GROUP = 5
        const val GROUP_MODULUS = 100_000L
    }
}

enum class ComparisonResult { MATCH, MISMATCH, NOT_A_COMPARISON_CODE }

@Singleton
class SafetyNumbers @Inject constructor(private val sodium: SodiumApi) {

    /**
     * The safety number for two Ed25519 identity keys, whichever order they're given in: BLAKE2b
     * (60 bytes) over a domain label and the two keys sorted byte by byte.
     */
    fun between(a: ByteArray, b: ByteArray): SafetyNumber {
        require(a.size == SodiumApi.ED25519_PUBLIC_BYTES && b.size == SodiumApi.ED25519_PUBLIC_BYTES) { "Not Ed25519 public keys" }
        val (low, high) = if (compareUnsigned(a, b) <= 0) a to b else b to a
        return SafetyNumber(sodium.genericHash(DIGEST_BYTES, CONTEXT + low + high))
    }

    /** The comparison QR's text. Both phones show the same one. */
    fun comparisonCode(number: SafetyNumber): String {
        val code = safetyNumberCode {
            version = VERSION
            digest = ByteString.copyFrom(number.digest)
        }
        return PREFIX + ENCODER.encodeToString(code.toByteArray())
    }

    /** Checks a scanned comparison code against [number], the safety number this phone computed. */
    fun compare(number: SafetyNumber, scannedText: String): ComparisonResult {
        if (!scannedText.startsWith(PREFIX)) return ComparisonResult.NOT_A_COMPARISON_CODE
        val code = try {
            SafetyNumberCode.parseFrom(DECODER.decode(scannedText.substring(PREFIX.length).trim()))
        } catch (e: IllegalArgumentException) {
            return ComparisonResult.NOT_A_COMPARISON_CODE
        } catch (e: InvalidProtocolBufferException) {
            return ComparisonResult.NOT_A_COMPARISON_CODE
        }
        if (code.version != VERSION || code.digest.size() != DIGEST_BYTES) return ComparisonResult.NOT_A_COMPARISON_CODE
        return if (sodium.constantTimeEquals(code.digest.toByteArray(), number.digest)) ComparisonResult.MATCH else ComparisonResult.MISMATCH
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (index in a.indices) {
            val difference = (a[index].toInt() and 0xFF) - (b[index].toInt() and 0xFF)
            if (difference != 0) return difference
        }
        return 0
    }

    companion object {
        const val PREFIX = "TFL-SN1:"
        private const val VERSION = 1
        private const val DIGEST_BYTES = SafetyNumber.GROUPS * SafetyNumber.BYTES_PER_GROUP
        private val CONTEXT = "TFL-safety-number-v1".encodeToByteArray() + 0.toByte()
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }
}
