package app.tfl.core.crypto

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.keystore.AndroidHardwareKeys
import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.lock.Argon2idHasher
import app.tfl.core.crypto.lock.KdfBenchmarker
import app.tfl.core.crypto.phrase.Bip39Wordlist
import app.tfl.core.crypto.phrase.RecoveryPhraseCodec
import app.tfl.core.crypto.sodium.KdfContext
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.security.KeyStorageLevel
import com.goterl.lazysodium.SodiumAndroid
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a phone: the Android libsodium build gives the same answers as the JVM tests, and the
 * hardware Keystore works. Also logs the Argon2id timings for this phone (tag "TFL-benchmark").
 */
@RunWith(AndroidJUnit4::class)
class DeviceCryptoTest {

    private val sodium = SodiumApi(SodiumAndroid())
    private val keys = AndroidHardwareKeys(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = keys.deleteAll()

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun androidLibsodiumMatchesTheKnownAnswers() {
        val seed = ByteArray(32) { it.toByte() }
        assertEquals("f045c77dcc2664f2de56bb9d2d078e9216465428c199fe6bb03efddb73358172", sodium.deriveKey(seed, 1, KdfContext.IDENTITY_SIGN).hex())
        assertEquals(
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
            sodium.ed25519KeyPair("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60".hex()).first.hex(),
        )
        assertEquals(
            "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a",
            sodium.x25519PublicKey("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hex()).hex(),
        )
        assertEquals("F162BE7D2746777DB5A0FF5D6329EC0E", Fingerprints(sodium).of("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".hex()))
        val codec = RecoveryPhraseCodec(sodium, Bip39Wordlist.load(sodium))
        assertEquals("zoo ".repeat(23) + "vote", codec.encode(ByteArray(32) { -1 }).joinToString(" "))
    }

    @Test
    fun hardwareKeysEncryptAndRejectTampering() {
        val level = keys.ensureKey(HardwareKeyAlias.STATE)
        Log.i("TFL-benchmark", "Keystore level: $level")
        assertNotEquals("keys should be hardware-backed on a phone", KeyStorageLevel.SOFTWARE, level)
        val blob = keys.encrypt(HardwareKeyAlias.STATE, byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), keys.decrypt(HardwareKeyAlias.STATE, blob))
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertThrows(AuthenticationException::class.java) { keys.decrypt(HardwareKeyAlias.STATE, blob) }
    }

    @Test
    fun argon2idBenchmark() {
        KdfBenchmarker(sodium, Argon2idHasher(sodium)).measure().forEach {
            Log.i("TFL-benchmark", "Argon2id ops=${it.params.opsLimit} mem=${it.params.memLimitMebibytes} MiB: ${it.millis ?: "failed"} ms")
        }
    }
}
