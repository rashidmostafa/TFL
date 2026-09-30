package app.tfl.core.crypto.keystore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import app.tfl.core.crypto.AuthenticationException
import app.tfl.core.crypto.CryptoException
import app.tfl.core.model.security.KeyStorageLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Android Keystore keys: StrongBox (a separate secure chip) when the phone has one, otherwise the TEE. */
@Singleton
class AndroidHardwareKeys @Inject constructor(
    @ApplicationContext private val context: Context,
) : HardwareKeys {

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    @Synchronized
    override fun ensureKey(alias: HardwareKeyAlias): KeyStorageLevel {
        if (keyStore.containsAlias(alias.alias)) return levelOf(alias)
        if (hasStrongBox()) {
            try {
                generate(alias, strongBox = true)
                return KeyStorageLevel.STRONGBOX
            } catch (_: StrongBoxUnavailableException) {
                // Advertised but unusable for this key type; fall back to the TEE below.
            } catch (_: ProviderException) {
            }
        }
        generate(alias, strongBox = false)
        return levelOf(alias)
    }

    override fun exists(alias: HardwareKeyAlias): Boolean = keyStore.containsAlias(alias.alias)

    override fun encrypt(alias: HardwareKeyAlias, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(alias)) }
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(alias: HardwareKeyAlias, blob: ByteArray): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        }
        cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    } catch (e: AEADBadTagException) {
        throw AuthenticationException("Hardware-encrypted data failed authentication")
    } catch (e: GeneralSecurityException) {
        throw CryptoException("Hardware key unavailable", e)
    }

    override fun biometricEncryptCipher(): Cipher {
        ensureKey(HardwareKeyAlias.BIOMETRIC)
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(HardwareKeyAlias.BIOMETRIC)) }
    }

    override fun biometricDecryptCipher(blob: ByteArray): Cipher = try {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(HardwareKeyAlias.BIOMETRIC), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        throw BiometricKeyInvalidatedException(e)
    }

    override fun finishBiometricEncrypt(authenticatedCipher: Cipher, plaintext: ByteArray): ByteArray =
        authenticatedCipher.iv + authenticatedCipher.doFinal(plaintext)

    override fun finishBiometricDecrypt(authenticatedCipher: Cipher, blob: ByteArray): ByteArray = try {
        authenticatedCipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    } catch (e: GeneralSecurityException) {
        throw AuthenticationException("Biometric unlock failed")
    }

    @Synchronized
    override fun delete(alias: HardwareKeyAlias) {
        if (keyStore.containsAlias(alias.alias)) keyStore.deleteEntry(alias.alias)
    }

    private fun key(alias: HardwareKeyAlias): SecretKey =
        keyStore.getKey(alias.alias, null) as? SecretKey ?: throw CryptoException("Hardware key ${alias.alias} is missing")

    private fun hasStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun generate(alias: HardwareKeyAlias, strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(alias.alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setRandomizedEncryptionRequired(true)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
                if (alias == HardwareKeyAlias.BIOMETRIC) requireBiometricPerUse()
            }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }

    private fun KeyGenParameterSpec.Builder.requireBiometricPerUse() {
        setUserAuthenticationRequired(true)
        setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(-1)
        }
    }

    private fun levelOf(alias: HardwareKeyAlias): KeyStorageLevel {
        val key = key(alias)
        val info = SecretKeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeyStorageLevel.STRONGBOX
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT, KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> KeyStorageLevel.TEE
                else -> KeyStorageLevel.SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) KeyStorageLevel.TEE else KeyStorageLevel.SOFTWARE
        }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
