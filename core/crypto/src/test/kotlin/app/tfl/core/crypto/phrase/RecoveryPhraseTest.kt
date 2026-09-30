package app.tfl.core.crypto.phrase

import app.tfl.core.crypto.hexToBytes
import app.tfl.core.crypto.toHex
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPhraseTest {

    private val sodium = JvmSodium.api
    private val wordlist = Bip39Wordlist.load(sodium)
    private val codec = RecoveryPhraseCodec(sodium, wordlist)

    /** Official BIP39 English test vectors (256-bit entropy), also reproduced with an independent Python encoder. */
    private val vectors = mapOf(
        "00".repeat(32) to "abandon ".repeat(23) + "art",
        "7f".repeat(32) to "legal winner thank year wave sausage worth useful legal winner thank year wave sausage " +
            "worth useful legal winner thank year wave sausage worth title",
        "80".repeat(32) to "letter advice cage absurd amount doctor acoustic avoid letter advice cage absurd amount " +
            "doctor acoustic avoid letter advice cage absurd amount doctor acoustic bless",
        "ff".repeat(32) to "zoo ".repeat(23) + "vote",
        "68a79eaca2324873eacc50cb9c6eca8cc68ea5d936f98787c60c7ebc74e6ce7c" to
            "hamster diagram private dutch cause delay private meat slide toddler razor book happy fancy gospel " +
            "tennis maple dilemma loan word shrug inflict delay length",
        "9f6a2878b2520799a44ef18bc7df394e7061a224d2c33cd015b157d746869863" to
            "panda eyebrow bullet gorilla call smoke muffin taste mesh discover soft ostrich alcohol speed nation " +
            "flash devote level hobby quick inner drive ghost inside",
    )

    @Test
    fun `wordlist is the pinned official list`() {
        assertEquals("abandon", wordlist.wordAt(0))
        assertEquals("zoo", wordlist.wordAt(2047))
        assertEquals(listOf("abandon"), wordlist.suggestions("aband"))
        assertEquals(listOf("zone", "zoo"), wordlist.suggestions("zo"))
        assertEquals(emptyList<String>(), wordlist.suggestions("qx"))
    }

    @Test
    fun `encodes the official BIP39 vectors`() {
        vectors.forEach { (entropy, phrase) ->
            assertEquals(phrase, codec.encode(entropy.hexToBytes()).joinToString(" "))
        }
    }

    @Test
    fun `decodes the official BIP39 vectors`() {
        vectors.forEach { (entropy, phrase) ->
            val result = codec.decode(phrase)
            assertTrue(phrase, result is PhraseResult.Valid)
            assertEquals(entropy, (result as PhraseResult.Valid).seed.toHex())
        }
    }

    @Test
    fun `random seeds survive a round trip`() {
        repeat(1000) {
            val seed = sodium.randomBytes(32)
            val decoded = codec.decode(codec.encode(seed))
            assertArrayEquals(seed, (decoded as PhraseResult.Valid).seed)
        }
    }

    @Test
    fun `case and surrounding spaces are ignored`() {
        val words = vectors.getValue("7f".repeat(32)).split(" ").map { "  ${it.uppercase()} " }
        assertTrue(codec.decode(words) is PhraseResult.Valid)
    }

    @Test
    fun `a wrong last word fails the checksum`() {
        assertEquals(PhraseResult.ChecksumMismatch, codec.decode("abandon ".repeat(24).trim()))
    }

    @Test
    fun `swapped words fail the checksum`() {
        val words = vectors.getValue("7f".repeat(32)).split(" ").toMutableList()
        words[0] = words[1].also { words[1] = words[0] }
        assertEquals(PhraseResult.ChecksumMismatch, codec.decode(words))
    }

    @Test
    fun `unknown words are reported by position`() {
        val words = vectors.getValue("00".repeat(32)).split(" ").toMutableList()
        words[6] = "bitcoin"
        assertEquals(PhraseResult.UnknownWord(6), codec.decode(words))
    }

    @Test
    fun `phrases must have exactly 24 words`() {
        val words = vectors.getValue("00".repeat(32)).split(" ")
        assertEquals(PhraseResult.WrongWordCount(23), codec.decode(words.drop(1)))
        assertEquals(PhraseResult.WrongWordCount(25), codec.decode(words + "abandon"))
    }
}
