package app.tfl.core.crypto.keystore

import app.tfl.core.crypto.CryptoException
import app.tfl.core.model.security.KeyStorageLevel
import javax.crypto.Cipher

/** TFL's non-exportable Android Keystore keys (AES-256-GCM). */
enum class HardwareKeyAlias(val alias: String) {
    /** Encrypts the lock-state file. Usable without user authentication. */
    STATE("tfl.state"),

    /** Wraps the unlock key for fingerprint unlock. Every use needs a strong biometric. */
    BIOMETRIC("tfl.bio"),

    /** Encrypts the locked inbox: what the transport receives while TFL is locked. No user authentication. */
    INBOX("tfl.inbox"),
}

/** The biometric key was invalidated, typically because a new fingerprint was enrolled. */
class BiometricKeyInvalidatedException(cause: Throwable) : CryptoException("Biometric key was invalidated", cause)

/**
 * Keys that never leave the phone's secure hardware. Blobs are `IV (12) ‖ ciphertext ‖ tag`.
 * Faked with plain JCE keys in JVM tests; the real implementation is [AndroidHardwareKeys].
 */
interface HardwareKeys {

    /** Creates [alias] if needed and reports where it lives. */
    fun ensureKey(alias: HardwareKeyAlias): KeyStorageLevel

    fun exists(alias: HardwareKeyAlias): Boolean

    /** Only for keys without user authentication. */
    fun encrypt(alias: HardwareKeyAlias, plaintext: ByteArray): ByteArray

    /** @throws CryptoException if the key is missing or the blob fails authentication. */
    fun decrypt(alias: HardwareKeyAlias, blob: ByteArray): ByteArray

    /** Cipher to hand to BiometricPrompt before [finishBiometricEncrypt]. Creates the biometric key. */
    fun biometricEncryptCipher(): Cipher

    /**
     * Cipher to hand to BiometricPrompt before [finishBiometricDecrypt].
     * @throws BiometricKeyInvalidatedException if enrolment changed since the key was created.
     */
    fun biometricDecryptCipher(blob: ByteArray): Cipher

    fun finishBiometricEncrypt(authenticatedCipher: Cipher, plaintext: ByteArray): ByteArray

    fun finishBiometricDecrypt(authenticatedCipher: Cipher, blob: ByteArray): ByteArray

    fun delete(alias: HardwareKeyAlias)

    /** Deletes every TFL key. Anything encrypted under them becomes unrecoverable. */
    fun deleteAll() = HardwareKeyAlias.entries.forEach(::delete)
}
