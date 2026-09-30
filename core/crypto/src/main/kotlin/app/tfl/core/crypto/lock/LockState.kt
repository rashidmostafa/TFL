package app.tfl.core.crypto.lock

import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel

/**
 * One PIN's verification data. [wrappedUnlockKey] is the profile's unlock key sealed with the
 * PIN-derived key; it's absent for a duress PIN in wipe mode and for the dummy slot used when no
 * duress PIN exists (whose salt and verifier are random and never match).
 */
class PinSlot(val salt: ByteArray, val verifier: ByteArray, val wrappedUnlockKey: ByteArray?)

/** A profile's database file and its key and master seed, both sealed with the profile's unlock key. */
class ProfileKeys(val databaseName: String, val wrappedDatabaseKey: ByteArray, val wrappedSeed: ByteArray)

/** Consecutive failed PIN attempts, timed on the boot clock so neither reboots nor clock changes help. */
data class AttemptRecord(val failures: Int, val bootCount: Int, val elapsedAtLastFailure: Long) {
    companion object {
        val NONE = AttemptRecord(failures = 0, bootCount = 0, elapsedAtLastFailure = 0)
    }
}

/** Salted BLAKE2b of the calculator disguise's secret code. */
class DisguiseCodeHash(val key: ByteArray, val hash: ByteArray)

/**
 * Everything TFL needs before it's unlocked. It's stored as `lockstate.bin`, encrypted with the
 * Keystore key `tfl.state`, and contains no secret in usable form.
 */
data class LockState(
    val kdf: KdfParams,
    val keyStorage: KeyStorageLevel,
    val real: PinSlot,
    val realKeys: ProfileKeys,
    val duressMode: DuressMode,
    val duress: PinSlot,
    val decoyKeys: ProfileKeys?,
    /** The real unlock key sealed with the biometric Keystore key, when fingerprint unlock is on. */
    val biometricUnlockKey: ByteArray?,
    val attempts: AttemptRecord,
    /** 0 means never wipe. */
    val wipeAfterFailures: Int,
    val disguise: DisguiseCodeHash?,
)
