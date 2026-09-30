package app.tfl.core.session

import app.tfl.core.crypto.lock.DuressSetup
import app.tfl.core.crypto.lock.KdfParams
import app.tfl.core.crypto.lock.PinChangeResult
import app.tfl.core.crypto.lock.PinVault
import app.tfl.core.crypto.lock.ProfileSecrets
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingValue
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.Profile
import app.tfl.core.session.di.WorkDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** The lock configuration as the Security screen shows it for the unlocked profile. */
data class LockSettingsState(
    val profile: Profile,
    val keyStorage: KeyStorageLevel,
    val kdf: KdfParams,
    val biometricEnabled: Boolean,
    /** False while a duress PIN is set: a forced fingerprint would open the real profile. */
    val biometricAllowed: Boolean,
    val duressMode: DuressMode,
    val wipeAfterFailures: Int,
    val disguiseEnabled: Boolean,
)

/**
 * Security-screen operations. In the decoy profile the PIN change updates the duress PIN, and the
 * biometric, duress and wipe settings are saved only in the decoy's own database: nothing done in the
 * decoy can change the real profile's lock configuration.
 */
@Singleton
class LockSettings @Inject constructor(
    private val session: AppSession,
    private val vault: PinVault,
    private val settings: SettingsRepository,
    private val identities: IdentityRepository,
    private val profileCreator: ProfileCreator,
    private val disguise: CalculatorDisguise,
    private val wipeController: WipeController,
    private val sodium: SodiumApi,
    @param:WorkDispatcher private val work: CoroutineDispatcher,
) {

    private val profile: Profile get() = checkNotNull(session.profile) { "Locked" }

    suspend fun state(): LockSettingsState = withContext(work) {
        val status = vault.status()
        when (profile) {
            Profile.REAL -> LockSettingsState(
                profile = Profile.REAL,
                keyStorage = status.keyStorage,
                kdf = status.kdf,
                biometricEnabled = status.biometricEnabled,
                biometricAllowed = status.duressMode == DuressMode.NONE,
                duressMode = status.duressMode,
                wipeAfterFailures = status.wipeAfterFailures,
                disguiseEnabled = disguise.isEnabled(),
            )
            Profile.DECOY -> LockSettingsState(
                profile = Profile.DECOY,
                keyStorage = status.keyStorage,
                kdf = status.kdf,
                biometricEnabled = settings.get(SettingKeys.DECOY_BIOMETRIC),
                biometricAllowed = settings.get(SettingKeys.DECOY_DURESS_MODE) == DuressMode.NONE,
                duressMode = settings.get(SettingKeys.DECOY_DURESS_MODE),
                wipeAfterFailures = settings.get(SettingKeys.DECOY_WIPE_AFTER),
                disguiseEnabled = disguise.isEnabled(),
            )
        }
    }

    /**
     * Checks the unlocked profile's PIN (the duress PIN in the decoy) before asking for a new one. It
     * doesn't count towards the lock screen's attempts; the Security screen limits tries itself.
     */
    suspend fun verifyPin(pin: ByteArray): Boolean = withContext(work) {
        try {
            vault.verifyPin(profile, pin)
        } finally {
            sodium.wipe(pin)
        }
    }

    suspend fun changePin(currentPin: ByteArray, newPin: ByteArray): PinChangeResult = withContext(work) {
        try {
            vault.changePin(profile, currentPin, newPin)
        } finally {
            sodium.wipe(currentPin, newPin)
        }
    }

    suspend fun setDuress(currentPin: ByteArray, duressPin: ByteArray, mode: DuressMode): PinChangeResult = withContext(work) {
        require(mode != DuressMode.NONE)
        try {
            when (profile) {
                Profile.REAL -> setRealDuress(currentPin, duressPin, mode)
                Profile.DECOY -> when {
                    !vault.verifyPin(Profile.DECOY, currentPin) -> PinChangeResult.WRONG_PIN
                    currentPin.contentEquals(duressPin) -> PinChangeResult.SAME_AS_OTHER_PIN
                    else -> {
                        settings.setAll(
                            SettingValue(SettingKeys.DECOY_DURESS_MODE, mode),
                            SettingValue(SettingKeys.DECOY_BIOMETRIC, false),
                        )
                        PinChangeResult.CHANGED
                    }
                }
            }
        } finally {
            sodium.wipe(currentPin, duressPin)
        }
    }

    suspend fun removeDuress(currentPin: ByteArray): Boolean = withContext(work) {
        try {
            when (profile) {
                Profile.REAL -> {
                    val decoyDatabase = vault.status().decoyDatabaseName
                    vault.removeDuress(currentPin).also { removed ->
                        if (removed) decoyDatabase?.let(wipeController::deleteDatabase)
                    }
                }
                Profile.DECOY -> vault.verifyPin(Profile.DECOY, currentPin).also { valid ->
                    if (valid) settings.set(SettingKeys.DECOY_DURESS_MODE, DuressMode.NONE)
                }
            }
        } finally {
            sodium.wipe(currentPin)
        }
    }

    /**
     * Cipher to authenticate with BiometricPrompt before [enableBiometric]. Null in the decoy, where
     * the prompt runs without a key and nothing real changes.
     */
    suspend fun biometricEnrolCipher(): Cipher? = withContext(work) {
        if (profile == Profile.REAL) vault.biometricEnrolCipher() else null
    }

    suspend fun enableBiometric(authenticatedCipher: Cipher?) = withContext(work) {
        when (profile) {
            Profile.REAL -> {
                val unlockKey = session.unlockKeyCopy()
                try {
                    vault.enableBiometric(unlockKey, checkNotNull(authenticatedCipher))
                } finally {
                    sodium.wipe(unlockKey)
                }
            }
            Profile.DECOY -> settings.set(SettingKeys.DECOY_BIOMETRIC, true)
        }
    }

    suspend fun disableBiometric() = withContext(work) {
        when (profile) {
            Profile.REAL -> vault.disableBiometric()
            Profile.DECOY -> settings.set(SettingKeys.DECOY_BIOMETRIC, false)
        }
    }

    suspend fun setWipeAfterFailures(count: Int) = withContext(work) {
        when (profile) {
            Profile.REAL -> vault.setWipeAfterFailures(count)
            Profile.DECOY -> settings.set(SettingKeys.DECOY_WIPE_AFTER, count)
        }
    }

    /** The disguise is phone-wide (it's just a launcher icon), so it works the same in either profile. */
    suspend fun enableDisguise(code: ByteArray) = withContext(work) {
        try {
            disguise.enable(code)
        } finally {
            sodium.wipe(code)
        }
    }

    suspend fun disableDisguise() = withContext(work) { disguise.disable() }

    private suspend fun setRealDuress(realPin: ByteArray, duressPin: ByteArray, mode: DuressMode): PinChangeResult {
        val previousDecoy = vault.status().decoyDatabaseName
        if (mode == DuressMode.WIPE) {
            return vault.setDuress(realPin, DuressSetup(duressPin, DuressMode.WIPE)).also { result ->
                if (result == PinChangeResult.CHANGED) previousDecoy?.let(wipeController::deleteDatabase)
            }
        }
        // Check the PIN before creating a whole decoy database.
        if (!vault.verifyPin(Profile.REAL, realPin)) return PinChangeResult.WRONG_PIN
        val decoySeed = sodium.randomBytes(KEY_BYTES)
        val decoyUnlockKey = sodium.randomBytes(KEY_BYTES)
        val decoy = profileCreator.create(
            displayName = identities.get()?.displayName.orEmpty(),
            seed = decoySeed,
            createdAtMillis = System.currentTimeMillis(),
            settings = listOf(SettingValue(SettingKeys.AUTO_LOCK, session.autoLock)),
        )
        try {
            val result = vault.setDuress(
                realPin,
                DuressSetup(duressPin, DuressMode.DECOY, decoyUnlockKey, ProfileSecrets(decoy.databaseName, decoy.databaseKey, decoySeed)),
            )
            if (result == PinChangeResult.CHANGED) {
                previousDecoy?.let(wipeController::deleteDatabase)
            } else {
                wipeController.deleteDatabase(decoy.databaseName)
            }
            return result
        } finally {
            sodium.wipe(decoySeed, decoyUnlockKey, decoy.databaseKey)
        }
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
