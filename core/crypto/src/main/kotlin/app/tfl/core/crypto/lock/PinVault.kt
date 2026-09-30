package app.tfl.core.crypto.lock

import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.keystore.HardwareKeys
import app.tfl.core.crypto.sodium.BlobLabel
import app.tfl.core.crypto.sodium.KdfContext
import app.tfl.core.crypto.sodium.SealedBox
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.Profile
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

/** A profile's secrets, handed to the vault to be sealed. The caller wipes its own copies afterwards. */
class ProfileSecrets(val databaseName: String, val databaseKey: ByteArray, val seed: ByteArray)

/** Everything the vault needs at the onboarding commit point. */
class VaultSetup(
    val pin: ByteArray,
    val kdf: KdfParams,
    val unlockKey: ByteArray,
    val real: ProfileSecrets,
    val duress: DuressSetup?,
)

/** A duress PIN. Decoy mode needs the decoy profile's unlock key and secrets; wipe mode needs neither. */
class DuressSetup(
    val pin: ByteArray,
    val mode: DuressMode,
    val decoyUnlockKey: ByteArray? = null,
    val decoy: ProfileSecrets? = null,
) {
    init {
        require(mode != DuressMode.NONE) { "Use removeDuress to turn the duress PIN off" }
        require((mode == DuressMode.DECOY) == (decoyUnlockKey != null && decoy != null)) { "Decoy mode needs decoy keys" }
    }
}

/** An unlocked profile. The caller owns [unlockKey] and [databaseKey] and must wipe them when locking. */
class OpenedProfile(
    val profile: Profile,
    val unlockKey: ByteArray,
    val databaseName: String,
    val databaseKey: ByteArray,
)

enum class WipeReason { DURESS_PIN, TOO_MANY_ATTEMPTS }

sealed interface UnlockResult {
    class Opened(val profile: OpenedProfile) : UnlockResult

    /** The duress PIN in wipe mode, or the last allowed failure with wipe-after-N on. */
    data class WipeRequired(val reason: WipeReason) : UnlockResult

    /** [attemptsBeforeWipe] is null when wipe-after-N is off. */
    data class WrongPin(val failures: Int, val retryAfterMillis: Long, val attemptsBeforeWipe: Int?) : UnlockResult

    /** A delay from earlier failures is still running; the PIN wasn't checked. */
    data class Throttled(val retryAfterMillis: Long) : UnlockResult
}

enum class PinChangeResult { CHANGED, WRONG_PIN, SAME_AS_OTHER_PIN }

/** What the lock screen and Security settings may show. Contains no secrets. */
data class VaultStatus(
    val kdf: KdfParams,
    val keyStorage: KeyStorageLevel,
    val duressMode: DuressMode,
    val biometricEnabled: Boolean,
    val wipeAfterFailures: Int,
    val disguiseCodeSet: Boolean,
    val failures: Int,
    val retryAfterMillis: Long,
    val realDatabaseName: String,
    val decoyDatabaseName: String?,
)

/**
 * The app lock. Owns `lockstate.bin` and every PIN, duress and biometric decision.
 *
 * PIN → Argon2id → root key → (verifier, key that unwraps the profile's unlock key). The unlock key
 * in turn unwraps the database key and master seed. Every method is synchronized, so the lock
 * state is never read and rewritten concurrently. All calls block (Argon2id takes about a second),
 * so call them off the main thread.
 */
@Singleton
class PinVault @Inject constructor(
    private val sodium: SodiumApi,
    private val sealedBox: SealedBox,
    private val hasher: PasswordHasher,
    private val store: LockStateStore,
    private val keys: HardwareKeys,
    private val clock: DeviceClock,
) {

    fun isSetUp(): Boolean = store.exists()

    /** Writes the lock state: the onboarding commit point. */
    @Synchronized
    fun create(setup: VaultSetup): KeyStorageLevel {
        check(!store.exists()) { "The vault already exists" }
        val duress = setup.duress
        require(duress == null || !duress.pin.contentEquals(setup.pin)) { "The duress PIN must differ from the PIN" }
        val level = keys.ensureKey(HardwareKeyAlias.STATE)
        store.write(
            LockState(
                kdf = setup.kdf,
                keyStorage = level,
                real = newSlot(setup.pin, setup.kdf, setup.unlockKey),
                realKeys = sealProfile(setup.unlockKey, setup.real),
                duressMode = duress?.mode ?: DuressMode.NONE,
                duress = duress?.let { newSlot(it.pin, setup.kdf, it.decoyUnlockKey) } ?: dummySlot(),
                decoyKeys = duress?.decoy?.let { sealProfile(duress.decoyUnlockKey!!, it) },
                biometricUnlockKey = null,
                attempts = AttemptRecord.NONE,
                wipeAfterFailures = 0,
                disguise = null,
            ),
        )
        return level
    }

    @Synchronized
    fun unlock(pin: ByteArray): UnlockResult {
        val state = store.read()
        val waiting = AttemptPolicy.remainingDelay(state.attempts, clock)
        if (waiting > 0) return UnlockResult.Throttled(waiting)

        // Record the attempt as failed *before* checking it: killing the app mid-check can't erase it.
        val failed = AttemptPolicy.failed(state.attempts, clock)
        store.write(state.copy(attempts = failed))

        // Always run both derivations and both comparisons, so the time taken is the same whichever
        // PIN matched, and whether or not a duress PIN exists.
        val realRoot = hasher.derive(pin, state.real.salt, state.kdf)
        val duressRoot = try {
            hasher.derive(pin, state.duress.salt, state.kdf)
        } catch (e: CryptoException) {
            sodium.wipe(realRoot)
            throw e
        }
        try {
            val isReal = matches(realRoot, state.real)
            val isDuress = matches(duressRoot, state.duress) && state.duressMode != DuressMode.NONE
            return when {
                isReal -> {
                    val unlockKey = unwrapUnlockKey(realRoot, state.real)
                    store.write(state.copy(attempts = AttemptRecord.NONE))
                    UnlockResult.Opened(openProfile(Profile.REAL, unlockKey, state.realKeys))
                }
                isDuress && state.duressMode == DuressMode.DECOY -> {
                    val unlockKey = unwrapUnlockKey(duressRoot, state.duress)
                    store.write(state.copy(attempts = AttemptRecord.NONE))
                    UnlockResult.Opened(openProfile(Profile.DECOY, unlockKey, checkNotNull(state.decoyKeys)))
                }
                isDuress -> UnlockResult.WipeRequired(WipeReason.DURESS_PIN)
                state.wipeAfterFailures > 0 && failed.failures >= state.wipeAfterFailures ->
                    UnlockResult.WipeRequired(WipeReason.TOO_MANY_ATTEMPTS)
                else -> UnlockResult.WrongPin(
                    failures = failed.failures,
                    retryAfterMillis = AttemptPolicy.delayAfter(failed.failures),
                    attemptsBeforeWipe = if (state.wipeAfterFailures > 0) state.wipeAfterFailures - failed.failures else null,
                )
            }
        } finally {
            sodium.wipe(realRoot, duressRoot)
        }
    }

    /** Checks [pin] against a profile's PIN without touching the attempt counter (for settings changes). */
    @Synchronized
    fun verifyPin(profile: Profile, pin: ByteArray): Boolean {
        val state = store.read()
        return isPinOf(pin, slotFor(state, profile), state.kdf)
    }

    /** Opens the profile's master seed (e.g. to show the recovery phrase). The caller wipes it. */
    @Synchronized
    fun openSeed(profile: Profile, unlockKey: ByteArray): ByteArray {
        val state = store.read()
        return sealedBox.open(unlockKey, profileKeys(state, profile).wrappedSeed, BlobLabel.SEED)
    }

    /**
     * In the decoy profile this changes the duress PIN: the decoy stays self-consistent and can never
     * change the real profile's PIN.
     */
    @Synchronized
    fun changePin(profile: Profile, currentPin: ByteArray, newPin: ByteArray): PinChangeResult {
        val state = store.read()
        val slot = slotFor(state, profile)
        val root = hasher.derive(currentPin, slot.salt, state.kdf)
        try {
            if (!matches(root, slot) || slot.wrappedUnlockKey == null) return PinChangeResult.WRONG_PIN
            if (profile == Profile.REAL && state.duressMode != DuressMode.NONE && isPinOf(newPin, state.duress, state.kdf)) {
                return PinChangeResult.SAME_AS_OTHER_PIN
            }
            val unlockKey = unwrapUnlockKey(root, slot)
            try {
                val replacement = newSlot(newPin, state.kdf, unlockKey)
                store.write(if (profile == Profile.REAL) state.copy(real = replacement) else state.copy(duress = replacement))
            } finally {
                sodium.wipe(unlockKey)
            }
            return PinChangeResult.CHANGED
        } finally {
            sodium.wipe(root)
        }
    }

    /** Sets or replaces the duress PIN. Turns biometric unlock off: a forced fingerprint would open the real profile. */
    @Synchronized
    fun setDuress(realPin: ByteArray, duress: DuressSetup): PinChangeResult {
        val state = store.read()
        if (!isPinOf(realPin, state.real, state.kdf)) return PinChangeResult.WRONG_PIN
        if (duress.pin.contentEquals(realPin)) return PinChangeResult.SAME_AS_OTHER_PIN
        store.write(
            state.copy(
                duressMode = duress.mode,
                duress = newSlot(duress.pin, state.kdf, duress.decoyUnlockKey),
                decoyKeys = duress.decoy?.let { sealProfile(duress.decoyUnlockKey!!, it) },
                biometricUnlockKey = null,
            ),
        )
        keys.delete(HardwareKeyAlias.BIOMETRIC)
        return PinChangeResult.CHANGED
    }

    /** Turns the duress PIN off. Returns false for a wrong PIN; the caller deletes any decoy database. */
    @Synchronized
    fun removeDuress(realPin: ByteArray): Boolean {
        val state = store.read()
        if (!isPinOf(realPin, state.real, state.kdf)) return false
        store.write(state.copy(duressMode = DuressMode.NONE, duress = dummySlot(), decoyKeys = null))
        return true
    }

    /** Cipher for BiometricPrompt, or null when fingerprint unlock is off. */
    @Synchronized
    fun biometricUnlockCipher(): Cipher? = store.read().biometricUnlockKey?.let(keys::biometricDecryptCipher)

    @Synchronized
    fun unlockWithBiometric(authenticatedCipher: Cipher): OpenedProfile {
        val state = store.read()
        val blob = state.biometricUnlockKey ?: throw CryptoException("Biometric unlock is off")
        val unlockKey = keys.finishBiometricDecrypt(authenticatedCipher, blob)
        store.write(state.copy(attempts = AttemptRecord.NONE))
        return openProfile(Profile.REAL, unlockKey, state.realKeys)
    }

    @Synchronized
    fun biometricEnrolCipher(): Cipher {
        check(store.read().duressMode == DuressMode.NONE) { "Biometric unlock is unavailable while a duress PIN is set" }
        return keys.biometricEncryptCipher()
    }

    @Synchronized
    fun enableBiometric(unlockKey: ByteArray, authenticatedCipher: Cipher) {
        val state = store.read()
        check(state.duressMode == DuressMode.NONE) { "Biometric unlock is unavailable while a duress PIN is set" }
        store.write(state.copy(biometricUnlockKey = keys.finishBiometricEncrypt(authenticatedCipher, unlockKey)))
    }

    @Synchronized
    fun disableBiometric() {
        if (store.exists()) store.write(store.read().copy(biometricUnlockKey = null))
        keys.delete(HardwareKeyAlias.BIOMETRIC)
    }

    /** 0 turns it off. */
    @Synchronized
    fun setWipeAfterFailures(count: Int) {
        require(count == 0 || count in 3..50) { "Unsupported wipe threshold" }
        store.write(store.read().copy(wipeAfterFailures = count))
    }

    @Synchronized
    fun setDisguiseCode(code: ByteArray) {
        val key = sodium.randomBytes(DISGUISE_KEY_BYTES)
        store.write(store.read().copy(disguise = DisguiseCodeHash(key, sodium.genericHash(DISGUISE_HASH_BYTES, code, key))))
    }

    @Synchronized
    fun clearDisguiseCode() = store.write(store.read().copy(disguise = null))

    @Synchronized
    fun matchesDisguiseCode(code: ByteArray): Boolean {
        val disguise = store.read().disguise ?: return false
        val hash = sodium.genericHash(DISGUISE_HASH_BYTES, code, disguise.key)
        return sodium.constantTimeEquals(hash, disguise.hash).also { sodium.wipe(hash) }
    }

    @Synchronized
    fun status(): VaultStatus {
        val state = store.read()
        return VaultStatus(
            kdf = state.kdf,
            keyStorage = state.keyStorage,
            duressMode = state.duressMode,
            biometricEnabled = state.biometricUnlockKey != null,
            wipeAfterFailures = state.wipeAfterFailures,
            disguiseCodeSet = state.disguise != null,
            failures = state.attempts.failures,
            retryAfterMillis = AttemptPolicy.remainingDelay(state.attempts, clock),
            realDatabaseName = state.realKeys.databaseName,
            decoyDatabaseName = state.decoyKeys?.databaseName,
        )
    }

    /** Crypto-shredding: deletes the hardware keys first, so any copy of the files left on flash is unreadable. */
    @Synchronized
    fun destroy() {
        keys.deleteAll()
        store.delete()
    }

    private fun newSlot(pin: ByteArray, kdf: KdfParams, unlockKey: ByteArray?): PinSlot {
        val salt = sodium.randomBytes(SodiumApi.ARGON2_SALT_BYTES)
        val root = hasher.derive(pin, salt, kdf)
        try {
            val verifier = sodium.deriveKey(root, SUBKEY_ID, KdfContext.PIN_VERIFIER)
            val wrapped = unlockKey?.let { key ->
                val kek = sodium.deriveKey(root, SUBKEY_ID, KdfContext.PIN_KEK)
                try {
                    sealedBox.seal(kek, key, BlobLabel.UNLOCK_KEY_BY_PIN)
                } finally {
                    sodium.wipe(kek)
                }
            }
            return PinSlot(salt, verifier, wrapped)
        } finally {
            sodium.wipe(root)
        }
    }

    /** Random salt and verifier: nothing ever matches it, but checking it costs the same as a real slot. */
    private fun dummySlot() = PinSlot(
        salt = sodium.randomBytes(SodiumApi.ARGON2_SALT_BYTES),
        verifier = sodium.randomBytes(SodiumApi.KEY_BYTES),
        wrappedUnlockKey = null,
    )

    private fun matches(root: ByteArray, slot: PinSlot): Boolean {
        val verifier = sodium.deriveKey(root, SUBKEY_ID, KdfContext.PIN_VERIFIER)
        return sodium.constantTimeEquals(verifier, slot.verifier).also { sodium.wipe(verifier) }
    }

    private fun isPinOf(pin: ByteArray, slot: PinSlot, kdf: KdfParams): Boolean {
        val root = hasher.derive(pin, slot.salt, kdf)
        return matches(root, slot).also { sodium.wipe(root) }
    }

    private fun unwrapUnlockKey(root: ByteArray, slot: PinSlot): ByteArray {
        val kek = sodium.deriveKey(root, SUBKEY_ID, KdfContext.PIN_KEK)
        try {
            return sealedBox.open(kek, checkNotNull(slot.wrappedUnlockKey), BlobLabel.UNLOCK_KEY_BY_PIN)
        } finally {
            sodium.wipe(kek)
        }
    }

    private fun sealProfile(unlockKey: ByteArray, secrets: ProfileSecrets) = ProfileKeys(
        databaseName = secrets.databaseName,
        wrappedDatabaseKey = sealedBox.seal(unlockKey, secrets.databaseKey, BlobLabel.DATABASE_KEY),
        wrappedSeed = sealedBox.seal(unlockKey, secrets.seed, BlobLabel.SEED),
    )

    private fun openProfile(profile: Profile, unlockKey: ByteArray, profileKeys: ProfileKeys) = OpenedProfile(
        profile = profile,
        unlockKey = unlockKey,
        databaseName = profileKeys.databaseName,
        databaseKey = sealedBox.open(unlockKey, profileKeys.wrappedDatabaseKey, BlobLabel.DATABASE_KEY),
    )

    private fun slotFor(state: LockState, profile: Profile): PinSlot = when (profile) {
        Profile.REAL -> state.real
        Profile.DECOY -> state.duress
    }

    private fun profileKeys(state: LockState, profile: Profile): ProfileKeys = when (profile) {
        Profile.REAL -> state.realKeys
        Profile.DECOY -> state.decoyKeys ?: throw CryptoException("There is no decoy profile")
    }

    private companion object {
        const val SUBKEY_ID = 1L
        const val DISGUISE_KEY_BYTES = 32
        const val DISGUISE_HASH_BYTES = 32
    }
}
