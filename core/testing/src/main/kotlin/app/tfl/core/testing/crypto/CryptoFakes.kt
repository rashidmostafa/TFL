package app.tfl.core.testing.crypto

import app.tfl.core.crypto.AuthenticationException
import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.keystore.BiometricKeyInvalidatedException
import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.keystore.HardwareKeys
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.lock.KdfParams
import app.tfl.core.crypto.lock.PasswordHasher
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.security.KeyStorageLevel
import com.goterl.lazysodium.SodiumJava
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * libsodium 1.0.20 on the JVM (bundled with lazysodium-java): the same library the app ships.
 *
 * lazysodium-java's own loader mangles the resource path ("linux64" becomes "linux6"), so the bundled
 * library is copied out here and loaded by absolute path instead.
 */
object JvmSodium {
    val api: SodiumApi by lazy { SodiumApi(SodiumJava(extractBundledLibrary().absolutePath)) }

    private fun extractBundledLibrary(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val arm = System.getProperty("os.arch").orEmpty().lowercase().let { it.contains("aarch64") || it.contains("arm64") }
        val resource = when {
            os.contains("mac") -> if (arm) "mac_arm/libsodium.dylib" else "mac/libsodium.dylib"
            os.contains("win") -> "windows64/libsodium.dll"
            arm -> "arm64/libsodium.so"
            else -> "linux64/libsodium.so"
        }
        val stream = SodiumJava::class.java.classLoader?.getResourceAsStream(resource)
            ?: error("lazysodium-java has no bundled libsodium for $os")
        val file = File.createTempFile("libsodium", resource.substringAfterLast('.').let { ".$it" })
        file.deleteOnExit()
        stream.use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }
}

/** Argon2id at libsodium's minimum cost, so vault tests run in milliseconds. */
val FAST_KDF = KdfParams(opsLimit = 1, memLimitBytes = 8 * 1024)

/**
 * Stands in for the Android Keystore with ordinary in-memory AES-256-GCM keys. The biometric
 * "authentication" always succeeds; set [biometricInvalidated] to mimic a new fingerprint enrolment.
 */
class FakeHardwareKeys(private val level: KeyStorageLevel = KeyStorageLevel.TEE) : HardwareKeys {

    private val keys = mutableMapOf<HardwareKeyAlias, SecretKey>()
    var biometricInvalidated = false

    val aliases: Set<HardwareKeyAlias> get() = keys.keys.toSet()

    override fun ensureKey(alias: HardwareKeyAlias): KeyStorageLevel {
        keys.getOrPut(alias) { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }
        return level
    }

    override fun exists(alias: HardwareKeyAlias): Boolean = alias in keys

    override fun encrypt(alias: HardwareKeyAlias, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(alias)) }
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(alias: HardwareKeyAlias, blob: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(128, blob, 0, IV_BYTES))
        }
        cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    } catch (e: AEADBadTagException) {
        throw AuthenticationException("Fake hardware decryption failed")
    }

    override fun biometricEncryptCipher(): Cipher {
        ensureKey(HardwareKeyAlias.BIOMETRIC)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(HardwareKeyAlias.BIOMETRIC)) }
    }

    override fun biometricDecryptCipher(blob: ByteArray): Cipher {
        if (biometricInvalidated) throw BiometricKeyInvalidatedException(IllegalStateException("enrolment changed"))
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(HardwareKeyAlias.BIOMETRIC), GCMParameterSpec(128, blob, 0, IV_BYTES))
        }
    }

    override fun finishBiometricEncrypt(authenticatedCipher: Cipher, plaintext: ByteArray): ByteArray =
        authenticatedCipher.iv + authenticatedCipher.doFinal(plaintext)

    override fun finishBiometricDecrypt(authenticatedCipher: Cipher, blob: ByteArray): ByteArray =
        authenticatedCipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)

    override fun delete(alias: HardwareKeyAlias) {
        keys.remove(alias)
    }

    private fun key(alias: HardwareKeyAlias): SecretKey = keys[alias] ?: throw CryptoException("Fake key ${alias.alias} is missing")

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/** Clocks the test controls. [wall] starts on 21 September 2026. */
class FakeDeviceClock(var elapsed: Long = 10_000_000, var boots: Int = 1, var wall: Long = 1_790_000_000_000) : DeviceClock {
    override fun elapsedRealtime(): Long = elapsed
    override fun bootCount(): Int = boots
    override fun currentTimeMillis(): Long = wall

    /** Time passes: both clocks move on. */
    fun advance(millis: Long) {
        elapsed += millis
        wall += millis
    }

    /** A reboot: the boot count goes up and time since boot starts again. */
    fun reboot(elapsedAfterBoot: Long = 5_000) {
        boots += 1
        elapsed = elapsedAfterBoot
    }
}

/** Wraps a hasher and counts derivations; [failNext] simulates the process dying mid-derivation. */
class CountingHasher(private val delegate: PasswordHasher) : PasswordHasher {
    var calls = 0
        private set
    var failNext = false

    override fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        calls++
        if (failNext) {
            failNext = false
            throw CryptoException("Simulated failure")
        }
        return delegate.derive(password, salt, params)
    }

    fun reset() {
        calls = 0
    }
}
