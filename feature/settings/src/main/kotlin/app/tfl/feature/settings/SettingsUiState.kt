package app.tfl.feature.settings

import androidx.compose.runtime.Immutable
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.PanicTrigger

@Immutable
data class SettingsUiState(
    val displayName: String = "",
    /** Public key fingerprint in 4-character groups; empty until loaded. */
    val fingerprint: List<String> = emptyList(),
    /** Where the hardware keys live; null until loaded. */
    val keyStorage: KeyStorageLevel? = null,
    /** Argon2id memory cost for the PIN; null until loaded. */
    val kdfMemoryMiB: Long? = null,
    val autoLock: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
)

/** What a PIN prompt on the Security screen is for. */
enum class PinPurpose { CHANGE_PIN, SET_DURESS, REMOVE_DURESS, DISGUISE_CODE }

enum class PinStage {
    /** Duress only: decoy or wipe. */
    CHOOSE_MODE,

    /** Disguise only: how it works, before choosing the code. */
    INTRO,

    /** The current PIN (the duress PIN in the decoy profile). */
    CURRENT,

    /** The new PIN or code, typed twice. */
    NEW,
    CONFIRM,
}

enum class PinError { WRONG_PIN, MISMATCH, SAME_AS_OTHER_PIN, CODE_IS_PIN, FAILED }

/** A PIN prompt in progress. Holds only progress, never digits. */
@Immutable
data class PinFlowUi(
    val purpose: PinPurpose,
    val stage: PinStage,
    val entered: Int = 0,
    val duressMode: DuressMode = DuressMode.DECOY,
    val error: PinError? = null,
    /** Argon2id is running (about a second). */
    val busy: Boolean = false,
)

enum class SecuritySheet { AUTO_LOCK, WIPE_AFTER }

/** Shown once as a snackbar. */
enum class SecurityMessage {
    PIN_CHANGED,
    DURESS_SET,
    DURESS_REMOVED,
    DISGUISE_ON,
    DISGUISE_OFF,
    BIOMETRIC_ON,
    BIOMETRIC_FAILED,
}

/** The protections this build can enforce. The first three are always on. */
enum class Protection { PIN_LOCK, ENCRYPTED_STORAGE, SCREENSHOT_BLOCKING, DURESS_PIN, WIPE_AFTER_FAILURES, CALCULATOR_DISGUISE }

@Immutable
data class SecuritySettingsUiState(
    val loaded: Boolean = false,
    val keyStorage: KeyStorageLevel = KeyStorageLevel.TEE,
    val kdfMemoryMiB: Long = 0,
    val autoLock: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    /** The phone has strong (Class 3) biometrics. */
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = false,
    /** False while a duress PIN is set: a forced fingerprint would open the real profile. */
    val biometricAllowed: Boolean = true,
    val duressMode: DuressMode = DuressMode.NONE,
    /** 0 = off. */
    val wipeAfterFailures: Int = 0,
    val disguiseEnabled: Boolean = false,
    /** Saved only: enforced in a later phase. */
    val faceDownLock: Boolean = false,
    /** Saved only: enforced in a later phase. */
    val panicTrigger: PanicTrigger = PanicTrigger.SHAKE,
    val pinFlow: PinFlowUi? = null,
    val sheet: SecuritySheet? = null,
    val message: SecurityMessage? = null,
) {
    val activeProtections: List<Protection>
        get() = Protection.entries.filter { protection ->
            when (protection) {
                Protection.PIN_LOCK, Protection.ENCRYPTED_STORAGE, Protection.SCREENSHOT_BLOCKING -> true
                Protection.DURESS_PIN -> duressMode != DuressMode.NONE
                Protection.WIPE_AFTER_FAILURES -> wipeAfterFailures > 0
                Protection.CALCULATOR_DISGUISE -> disguiseEnabled
            }
        }
}

enum class NetworkToggle { TOR, NEARBY, RELAY, BATTERY_SAVER, PAUSE_RELAY_ON_LOW_BATTERY }

/** Sample figures for the mesh card until transports exist (Phases 3 and 4). */
@Immutable
data class NetworkPreview(
    val peersNearby: Int,
    val txRate: String,
    val rxRate: String,
    val roundTrip: String,
    /** Recent link activity, 0..1 per bar. */
    val linkActivity: List<Float>,
    val relayUsedMb: Int,
    val relayQuotaMb: Int,
    val relayResetsIn: String,
)

/** Saved choices. They take effect when the transports arrive. */
@Immutable
data class NetworkSettingsUiState(
    val toggles: Map<NetworkToggle, Boolean>,
    val bridgeMode: BridgeMode,
    val preview: NetworkPreview,
    val showBridges: Boolean = false,
) {
    fun isOn(toggle: NetworkToggle): Boolean = toggles[toggle] == true
}
