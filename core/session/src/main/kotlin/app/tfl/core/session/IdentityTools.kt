package app.tfl.core.session

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.phrase.Bip39Wordlist
import app.tfl.core.crypto.phrase.PhraseResult
import app.tfl.core.crypto.phrase.RecoveryPhraseCodec
import app.tfl.core.crypto.sodium.SodiumApi
import javax.inject.Inject
import javax.inject.Singleton

/** What onboarding needs from the crypto module, without handing it raw keys. */
@Singleton
class IdentityTools @Inject constructor(
    private val sodium: SodiumApi,
    private val derivation: IdentityKeyDerivation,
    private val fingerprints: Fingerprints,
    private val codec: RecoveryPhraseCodec,
    private val wordlist: Bip39Wordlist,
) {
    /** A fresh 32-byte master seed from libsodium's CSPRNG. The caller wipes it. */
    fun newSeed(): ByteArray = sodium.randomBytes(SEED_BYTES)

    /** The fingerprint this seed's identity will have. */
    fun fingerprintOf(seed: ByteArray): String = derivation.derive(seed).use { fingerprints.of(it.signPublicKey) }

    fun decodePhrase(words: List<String>): PhraseResult = codec.decode(words)

    fun encodePhrase(seed: ByteArray): List<String> = codec.encode(seed)

    fun suggestions(prefix: String, limit: Int = 4): List<String> = wordlist.suggestions(prefix, limit)

    fun isWord(word: String): Boolean = wordlist.contains(word.trim().lowercase())

    fun wipe(vararg secrets: ByteArray?) = sodium.wipe(*secrets)

    private companion object {
        const val SEED_BYTES = 32
    }
}
