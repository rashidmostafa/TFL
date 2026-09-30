package app.tfl.core.crypto.phrase

import app.tfl.core.crypto.sodium.SodiumApi
import javax.inject.Inject

/** Result of reading a recovery phrase back into a seed. */
sealed interface PhraseResult {
    /** The phrase is valid. The caller owns [seed] and must wipe it. */
    class Valid(val seed: ByteArray) : PhraseResult

    data class WrongWordCount(val count: Int) : PhraseResult

    /** [position] is 0-based. */
    data class UnknownWord(val position: Int) : PhraseResult

    /** Every word is valid but the checksum isn't: a word was misread or swapped. */
    data object ChecksumMismatch : PhraseResult
}

/**
 * The 32-byte master seed as 24 BIP39 words: 256 bits plus an 8-bit checksum (the first byte of
 * SHA-256 of the seed), split into 24 × 11-bit word indices.
 *
 * This is BIP39's *encoding* only. The seed is the entropy itself; BIP39's PBKDF2 step isn't used,
 * so the phrase is not interchangeable with cryptocurrency wallets.
 */
class RecoveryPhraseCodec @Inject constructor(
    private val sodium: SodiumApi,
    private val wordlist: Bip39Wordlist,
) {

    fun encode(seed: ByteArray): List<String> {
        require(seed.size == SEED_BYTES) { "Seed must be 32 bytes" }
        val checksum = sodium.sha256(seed)
        val data = seed + checksum[0]
        try {
            return toIndices(data).map { wordlist.wordAt(it) }
        } finally {
            sodium.wipe(data, checksum)
        }
    }

    /** Accepts any case and surrounding whitespace in each word. */
    fun decode(words: List<String>): PhraseResult {
        val normalized = words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (normalized.size != WORDS) return PhraseResult.WrongWordCount(normalized.size)
        val indices = IntArray(WORDS)
        normalized.forEachIndexed { position, word ->
            indices[position] = wordlist.indexOf(word) ?: return PhraseResult.UnknownWord(position)
        }
        val data = fromIndices(indices)
        val seed = data.copyOf(SEED_BYTES)
        val expected = sodium.sha256(seed)
        val valid = expected[0] == data[SEED_BYTES]
        sodium.wipe(data, expected)
        indices.fill(0)
        if (!valid) {
            sodium.wipe(seed)
            return PhraseResult.ChecksumMismatch
        }
        return PhraseResult.Valid(seed)
    }

    fun decode(phrase: String): PhraseResult = decode(phrase.trim().split(Regex("\\s+")))

    private fun toIndices(data: ByteArray): IntArray {
        val out = IntArray(WORDS)
        var accumulator = 0
        var bits = 0
        var next = 0
        for (byte in data) {
            accumulator = (accumulator shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= BITS_PER_WORD) {
                bits -= BITS_PER_WORD
                out[next++] = (accumulator ushr bits) and WORD_MASK
                accumulator = accumulator and ((1 shl bits) - 1)
            }
        }
        return out
    }

    private fun fromIndices(indices: IntArray): ByteArray {
        val out = ByteArray(SEED_BYTES + 1)
        var accumulator = 0
        var bits = 0
        var next = 0
        for (index in indices) {
            accumulator = (accumulator shl BITS_PER_WORD) or index
            bits += BITS_PER_WORD
            while (bits >= 8) {
                bits -= 8
                out[next++] = (accumulator ushr bits).toByte()
                accumulator = accumulator and ((1 shl bits) - 1)
            }
        }
        return out
    }

    companion object {
        const val WORDS = 24
        const val SEED_BYTES = 32
        private const val BITS_PER_WORD = 11
        private const val WORD_MASK = 0x7FF
    }
}
