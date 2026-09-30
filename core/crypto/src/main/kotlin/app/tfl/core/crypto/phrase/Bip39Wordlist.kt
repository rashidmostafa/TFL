package app.tfl.core.crypto.phrase

import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.sodium.SodiumApi

/** The official BIP39 English wordlist: 2048 sorted, lowercase words, each unique in its first 4 letters. */
class Bip39Wordlist private constructor(private val words: List<String>) {

    private val indices: Map<String, Int> = words.withIndex().associate { (index, word) -> word to index }

    fun indexOf(word: String): Int? = indices[word]

    fun wordAt(index: Int): String = words[index]

    fun contains(word: String): Boolean = word in indices

    /** Up to [limit] words starting with [prefix], for autocomplete. */
    fun suggestions(prefix: String, limit: Int = 4): List<String> {
        val normalized = prefix.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        val start = words.binarySearch(normalized).let { if (it >= 0) it else -it - 1 }
        return words.subList(start, words.size).asSequence().takeWhile { it.startsWith(normalized) }.take(limit).toList()
    }

    companion object {
        const val SIZE = 2048
        const val SHA256 = "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda"
        private const val RESOURCE = "/app/tfl/core/crypto/phrase/bip39_english.txt"

        /** Loads the bundled list and refuses to run if it doesn't match the pinned SHA-256. */
        fun load(sodium: SodiumApi): Bip39Wordlist {
            val bytes = Bip39Wordlist::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
                ?: throw CryptoException("Recovery wordlist is missing")
            val hash = sodium.sha256(bytes).joinToString("") { "%02x".format(it) }
            if (hash != SHA256) throw CryptoException("Recovery wordlist is corrupted")
            val words = bytes.decodeToString().split('\n').filter { it.isNotEmpty() }
            if (words.size != SIZE) throw CryptoException("Recovery wordlist has the wrong size")
            return Bip39Wordlist(words)
        }
    }
}
