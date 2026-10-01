package app.tfl.core.session

import app.tfl.core.common.log.TflLog
import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.keystore.BiometricKeyInvalidatedException
import app.tfl.core.crypto.lock.DuressSetup
import app.tfl.core.crypto.lock.OpenedProfile
import app.tfl.core.crypto.lock.PinCostPolicy
import app.tfl.core.crypto.lock.PinVault
import app.tfl.core.crypto.lock.ProfileSecrets
import app.tfl.core.crypto.lock.UnlockResult
import app.tfl.core.crypto.lock.VaultSetup
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingValue
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.Profile
import app.tfl.core.session.di.WorkDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** What the app shows. The activity switches its whole content on this. */
sealed interface GateState {
    data object Starting : GateState

    /** No identity on this phone yet. */
    data object NeedsOnboarding : GateState

    data object Locked : GateState

    /** [onboardingStep] is DONE, or where onboarding resumes after the commit point. */
    data class Unlocked(val profile: Profile, val onboardingStep: OnboardingStep) : GateState

    /** The lock state can't be read (for example, its hardware key is gone): wipe and restore. */
    data object KeysUnavailable : GateState

    /** A wipe is running; the process restarts into a fresh app. */
    data object Wiping : GateState
}

sealed interface UnlockOutcome {
    data object Unlocked : UnlockOutcome
    data class WrongPin(val failures: Int, val retryAfterMillis: Long, val attemptsBeforeWipe: Int?) : UnlockOutcome
    data class Throttled(val retryAfterMillis: Long) : UnlockOutcome
    data object Wiped : UnlockOutcome
    data object Failed : UnlockOutcome
}

/** Everything onboarding collected before the commit point. The session wipes the secrets. */
class OnboardingDraft(
    val seed: ByteArray,
    val restored: Boolean,
    val displayName: String,
    val pin: ByteArray,
    val duressPin: ByteArray?,
    val duressMode: DuressMode,
    val autoLock: AutoLockTimeout,
    /** Ignored when a duress PIN is set: fingerprint unlock is off while one exists. */
    val wantsBiometric: Boolean = false,
)

/** What the lock screen may show. Contains no secrets. */
data class LockScreenInfo(
    val biometricEnabled: Boolean,
    val retryAfterMillis: Long,
    val wipeAfterFailures: Int,
    val failures: Int,
)

/**
 * TFL's lock gate. Holds the unlocked profile's unlock key in memory while unlocked and wipes it on
 * [lock]. The database is open exactly while unlocked. Each unlock also hands the profile's
 * transport keys to [TransportKeyring], which decides how long they outlive the unlock.
 */
@Singleton
class AppSession @Inject constructor(
    private val vault: PinVault,
    private val database: DatabaseHolder,
    private val settings: SettingsRepository,
    private val profileCreator: ProfileCreator,
    private val pinCost: PinCostPolicy,
    private val sodium: SodiumApi,
    private val wipeController: WipeController,
    private val identityTools: IdentityTools,
    private val derivation: IdentityKeyDerivation,
    private val keyring: TransportKeyring,
    @param:WorkDispatcher private val work: CoroutineDispatcher,
) {
    private val gate = MutableStateFlow<GateState>(GateState.Starting)
    private val mutex = Mutex()

    private var unlockKey: ByteArray? = null

    val state: StateFlow<GateState> = gate.asStateFlow()

    val profile: Profile? get() = (gate.value as? GateState.Unlocked)?.profile

    val isUnlocked: Boolean get() = gate.value is GateState.Unlocked

    /** Read on unlock and whenever it changes; the auto-lock timer uses it. */
    @Volatile
    var autoLock: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY
        private set

    /** Decides the first screen: onboarding when this phone has no identity, else the lock screen. */
    suspend fun start() = mutex.withLock {
        if (gate.value != GateState.Starting) return@withLock
        withContext(work) {
            gate.value = if (vault.isSetUp()) {
                GateState.Locked
            } else {
                // Files left by an onboarding that crashed before its commit point.
                wipeController.deleteProfileDatabases()
                GateState.NeedsOnboarding
            }
        }
    }

    suspend fun lockScreenInfo(): LockScreenInfo? = withContext(work) {
        runCatching { vault.status() }.getOrNull()?.let {
            LockScreenInfo(it.biometricEnabled, it.retryAfterMillis, it.wipeAfterFailures, it.failures)
        }
    }

    suspend fun unlock(pin: ByteArray): UnlockOutcome = mutex.withLock {
        withContext(work) {
            val result = try {
                vault.unlock(pin)
            } catch (e: CryptoException) {
                TflLog.w(TAG, e) { "Unlock failed" }
                gate.value = GateState.KeysUnavailable
                return@withContext UnlockOutcome.Failed
            } finally {
                sodium.wipe(pin)
            }
            when (result) {
                is UnlockResult.Opened -> openSession(result.profile)
                is UnlockResult.WrongPin -> UnlockOutcome.WrongPin(result.failures, result.retryAfterMillis, result.attemptsBeforeWipe)
                is UnlockResult.Throttled -> UnlockOutcome.Throttled(result.retryAfterMillis)
                is UnlockResult.WipeRequired -> {
                    wipeLocked()
                    UnlockOutcome.Wiped
                }
            }
        }
    }

    /** Cipher for BiometricPrompt, or null when fingerprint unlock is off or was invalidated. */
    suspend fun biometricUnlockCipher(): Cipher? = withContext(work) {
        try {
            vault.biometricUnlockCipher()
        } catch (e: BiometricKeyInvalidatedException) {
            vault.disableBiometric()
            null
        } catch (e: CryptoException) {
            null
        }
    }

    suspend fun unlockWithBiometric(authenticatedCipher: Cipher): UnlockOutcome = mutex.withLock {
        withContext(work) {
            try {
                openSession(vault.unlockWithBiometric(authenticatedCipher))
            } catch (e: CryptoException) {
                UnlockOutcome.Failed
            }
        }
    }

    /**
     * The onboarding commit point. Creates the profile database(s), then writes the lock state last:
     * a crash before that leaves no lock state, and the next start cleans up and begins again.
     */
    suspend fun commitOnboarding(draft: OnboardingDraft): KeyStorageLevel = mutex.withLock {
        withContext(work) {
            check(!vault.isSetUp()) { "An identity already exists" }
            val now = System.currentTimeMillis()
            val step = if (draft.restored) OnboardingStep.PERMISSIONS else OnboardingStep.RECOVERY_PHRASE
            val newUnlockKey = sodium.randomBytes(KEY_BYTES)
            var real: CreatedProfile? = null
            var decoy: CreatedProfile? = null
            var decoySeed: ByteArray? = null
            var decoyUnlockKey: ByteArray? = null
            try {
                real = profileCreator.create(
                    draft.displayName, draft.seed, now,
                    listOf(
                        SettingValue(SettingKeys.AUTO_LOCK, draft.autoLock),
                        SettingValue(SettingKeys.ONBOARDING_STEP, step),
                        SettingValue(SettingKeys.ONBOARDING_RESTORED, draft.restored),
                        SettingValue(SettingKeys.BIOMETRIC_REQUESTED, draft.wantsBiometric && draft.duressPin == null),
                    ),
                )
                val duress = draft.duressPin?.let { duressPin ->
                    if (draft.duressMode == DuressMode.DECOY) {
                        val seed = sodium.randomBytes(KEY_BYTES).also { decoySeed = it }
                        val key = sodium.randomBytes(KEY_BYTES).also { decoyUnlockKey = it }
                        val created = profileCreator.create(
                            draft.displayName, seed, now,
                            listOf(SettingValue(SettingKeys.AUTO_LOCK, draft.autoLock)),
                        ).also { decoy = it }
                        DuressSetup(duressPin, DuressMode.DECOY, key, ProfileSecrets(created.databaseName, created.databaseKey, seed))
                    } else {
                        DuressSetup(duressPin, draft.duressMode)
                    }
                }
                val level = vault.create(
                    VaultSetup(
                        pin = draft.pin,
                        kdf = pinCost.choose(),
                        unlockKey = newUnlockKey,
                        real = ProfileSecrets(real.databaseName, real.databaseKey, draft.seed),
                        duress = duress,
                    ),
                )
                database.open(real.databaseName, real.databaseKey)
                derivation.derive(draft.seed).use { keyring.hold(it.forTransport()) }
                unlockKey = newUnlockKey
                autoLock = draft.autoLock
                gate.value = GateState.Unlocked(Profile.REAL, step)
                level
            } catch (e: Exception) {
                real?.let { wipeController.deleteDatabase(it.databaseName) }
                decoy?.let { wipeController.deleteDatabase(it.databaseName) }
                sodium.wipe(newUnlockKey)
                throw e
            } finally {
                sodium.wipe(draft.seed, draft.pin, draft.duressPin, real?.databaseKey, decoy?.databaseKey, decoySeed, decoyUnlockKey)
            }
        }
    }

    /** The recovery phrase of the unlocked real profile (onboarding shows it once). */
    suspend fun recoveryPhrase(): List<String> = withContext(work) {
        val key = checkNotNull(unlockKey) { "Locked" }
        check(profile == Profile.REAL) { "Only the real profile has a phrase to show" }
        val seed = vault.openSeed(Profile.REAL, key)
        try {
            identityTools.encodePhrase(seed)
        } finally {
            sodium.wipe(seed)
        }
    }

    /** Hands the transport the unlocked profile's keys again, after it stopped and wiped them. */
    suspend fun refreshTransportKeys() = mutex.withLock {
        withContext(work) {
            val key = unlockKey ?: return@withContext
            handOverTransportKeys(profile ?: return@withContext, key)
        }
    }

    suspend fun advanceOnboarding(next: OnboardingStep) = mutex.withLock {
        val current = gate.value as? GateState.Unlocked ?: return@withLock
        settings.set(SettingKeys.ONBOARDING_STEP, next)
        gate.value = current.copy(onboardingStep = next)
    }

    suspend fun setAutoLock(timeout: AutoLockTimeout) {
        settings.set(SettingKeys.AUTO_LOCK, timeout)
        autoLock = timeout
    }

    /**
     * A copy of the unlock key for an operation that needs it (e.g. enabling biometrics). The caller wipes it.
     * @throws DatabaseLockedException while locked.
     */
    internal fun unlockKeyCopy(): ByteArray = (unlockKey ?: throw DatabaseLockedException()).copyOf()

    /** Whether onboarding restored this identity from a phrase (it then has no phrase step). */
    suspend fun wasRestored(): Boolean = settings.get(SettingKeys.ONBOARDING_RESTORED)

    /**
     * Closes the database and wipes the unlock key, and the transport keys unless the background
     * service keeps them. Waits for an unlock in progress to finish first.
     */
    suspend fun lock() = mutex.withLock {
        if (!isUnlocked) return@withLock
        closeSession()
        keyring.locked()
        gate.value = GateState.Locked
    }

    /** Deletes TFL's keys and data and restarts into a fresh app. */
    suspend fun wipe() = mutex.withLock { withContext(work) { wipeLocked() } }

    private fun wipeLocked() {
        gate.value = GateState.Wiping
        closeSession()
        wipeController.wipeEverything()
    }

    private suspend fun openSession(opened: OpenedProfile): UnlockOutcome {
        try {
            database.open(opened.databaseName, opened.databaseKey)
        } catch (e: Exception) {
            TflLog.w(TAG, e) { "Profile database didn't open" }
            sodium.wipe(opened.unlockKey)
            gate.value = GateState.KeysUnavailable
            return UnlockOutcome.Failed
        } finally {
            sodium.wipe(opened.databaseKey)
        }
        unlockKey = opened.unlockKey
        handOverTransportKeys(opened.profile, opened.unlockKey)
        autoLock = settings.get(SettingKeys.AUTO_LOCK)
        gate.value = GateState.Unlocked(opened.profile, settings.get(SettingKeys.ONBOARDING_STEP))
        return UnlockOutcome.Unlocked
    }

    /** Derives [profile]'s keys from its seed for the transport; the seed is wiped straight after. */
    private fun handOverTransportKeys(profile: Profile, key: ByteArray) {
        try {
            val seed = vault.openSeed(profile, key)
            try {
                derivation.derive(seed).use { keyring.hold(it.forTransport()) }
            } finally {
                sodium.wipe(seed)
            }
        } catch (e: CryptoException) {
            // Messages then wait in the outbox; the rest of TFL works.
            TflLog.w(TAG, e) { "Transport keys unavailable" }
        }
    }

    @Synchronized
    private fun closeSession() {
        unlockKey?.let(sodium::wipe)
        unlockKey = null
        database.close()
    }

    private companion object {
        const val TAG = "AppSession"
        const val KEY_BYTES = 32
    }
}
