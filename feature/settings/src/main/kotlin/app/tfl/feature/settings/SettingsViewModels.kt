package app.tfl.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.common.log.TflLog
import app.tfl.core.crypto.lock.PinChangeResult
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.SettingKey
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.PanicTrigger
import app.tfl.core.session.AppSession
import app.tfl.core.session.LockSettings
import app.tfl.core.session.LockSettingsState
import app.tfl.core.session.PinEntry
import app.tfl.core.session.biometric.BiometricAuth
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.feature.settings.fake.FakeSettingsData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

private const val TAG = "Settings"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    identities: IdentityRepository,
    settings: SettingsRepository,
    private val lockSettings: LockSettings,
) : ViewModel() {

    private val lockFacts = MutableStateFlow<LockSettingsState?>(null)

    val uiState: StateFlow<SettingsUiState> =
        combine(identities.identity, settings.observe(SettingKeys.AUTO_LOCK), lockFacts) { identity, autoLock, lock ->
            SettingsUiState(
                displayName = identity?.displayName.orEmpty(),
                fingerprint = identity?.fingerprintGroups.orEmpty(),
                keyStorage = lock?.keyStorage,
                kdfMemoryMiB = lock?.kdf?.memLimitMebibytes,
                autoLock = autoLock,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            lockFacts.value = try {
                lockSettings.state()
            } catch (e: Exception) {
                // Locked in the meantime, or the lock state is unreadable (the gate handles both).
                TflLog.w(TAG, e) { "Lock facts unavailable" }
                null
            }
        }
    }
}

/**
 * The Security screen. PIN prompts collect digits into byte arrays through [PinEntry]; every PIN is
 * wiped once used. Three wrong current PINs in a row lock TFL, so the prompts here can't be used to
 * guess the PIN without the lock screen's delays.
 */
@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: AppSession,
    private val lockSettings: LockSettings,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val state = MutableStateFlow(SecuritySettingsUiState())
    val uiState: StateFlow<SecuritySettingsUiState> = state.asStateFlow()

    private val currentEntry = PinEntry(confirm = false)
    private val newEntry = PinEntry(confirm = true)

    /** The verified current PIN, kept between the prompt's steps. */
    private var currentPin: ByteArray? = null
    private var wrongPins = 0

    init {
        launchSafely { reload() }
    }

    fun showSheet(sheet: SecuritySheet?) = state.update { it.copy(sheet = sheet) }

    fun messageShown() = state.update { it.copy(message = null) }

    fun setAutoLock(timeout: AutoLockTimeout) = launchSafely {
        session.setAutoLock(timeout)
        state.update { it.copy(autoLock = timeout, sheet = null) }
    }

    fun setWipeAfterFailures(count: Int) = launchSafely {
        lockSettings.setWipeAfterFailures(count)
        state.update { it.copy(wipeAfterFailures = count, sheet = null) }
    }

    fun setFaceDownLock(enabled: Boolean) = launchSafely {
        settings.set(SettingKeys.FACE_DOWN_LOCK, enabled)
        state.update { it.copy(faceDownLock = enabled) }
    }

    fun setPanicTrigger(trigger: PanicTrigger) = launchSafely {
        settings.set(SettingKeys.PANIC_TRIGGER, trigger)
        state.update { it.copy(panicTrigger = trigger) }
    }

    /** The disguise needs a secret code to turn on; turning it off needs nothing. */
    fun setDisguise(enabled: Boolean) {
        if (enabled) {
            startPinFlow(PinPurpose.DISGUISE_CODE)
        } else {
            launchSafely {
                lockSettings.disableDisguise()
                state.update { it.copy(disguiseEnabled = false, message = SecurityMessage.DISGUISE_OFF) }
            }
        }
    }

    fun disableBiometric() = launchSafely {
        lockSettings.disableBiometric()
        reload()
    }

    /**
     * What the enrolment prompt needs: a Keystore cipher in the real profile, none in the decoy (where
     * nothing real changes). Null if the key can't be created, e.g. no fingerprint is enrolled.
     */
    suspend fun biometricEnrolment(): BiometricEnrolment? = try {
        BiometricEnrolment(lockSettings.biometricEnrolCipher())
    } catch (e: Exception) {
        TflLog.w(TAG, e) { "Biometric key unavailable" }
        null
    }

    fun onBiometricEnrolled(outcome: BiometricOutcome) {
        when (outcome) {
            is BiometricOutcome.Success -> launchSafely {
                val message = try {
                    lockSettings.enableBiometric(outcome.cipher)
                    SecurityMessage.BIOMETRIC_ON
                } catch (e: Exception) {
                    TflLog.w(TAG, e) { "Biometric enrolment failed" }
                    SecurityMessage.BIOMETRIC_FAILED
                }
                reload()
                state.update { it.copy(message = message) }
            }
            is BiometricOutcome.Failed -> state.update { it.copy(message = SecurityMessage.BIOMETRIC_FAILED) }
            BiometricOutcome.Cancelled -> Unit
        }
    }

    fun startPinFlow(purpose: PinPurpose) {
        resetEntries()
        val first = when (purpose) {
            PinPurpose.SET_DURESS -> PinStage.CHOOSE_MODE
            PinPurpose.DISGUISE_CODE -> PinStage.INTRO
            PinPurpose.CHANGE_PIN, PinPurpose.REMOVE_DURESS -> PinStage.CURRENT
        }
        state.update {
            val mode = if (it.duressMode == DuressMode.NONE) DuressMode.DECOY else it.duressMode
            it.copy(pinFlow = PinFlowUi(purpose, first, duressMode = mode))
        }
    }

    fun chooseDuressMode(mode: DuressMode) = updateFlow { it.copy(duressMode = mode) }

    /** On from the prompt's first page: the duress mode choice, or the disguise's explanation. */
    fun proceed() = updateFlow {
        when (it.stage) {
            PinStage.CHOOSE_MODE -> it.copy(stage = PinStage.CURRENT)
            PinStage.INTRO -> it.copy(stage = PinStage.NEW)
            else -> it
        }
    }

    fun cancelPinFlow() {
        resetEntries()
        state.update { it.copy(pinFlow = null) }
    }

    fun pinDigit(digit: Int) {
        val flow = state.value.pinFlow ?: return
        if (flow.busy) return
        when (flow.stage) {
            PinStage.CHOOSE_MODE, PinStage.INTRO -> Unit
            PinStage.CURRENT -> {
                currentEntry.digit(digit)
                if (currentEntry.stage == PinEntry.Stage.COMPLETE) checkCurrentPin(flow) else syncFlow()
            }
            PinStage.NEW, PinStage.CONFIRM -> {
                newEntry.digit(digit)
                if (newEntry.stage == PinEntry.Stage.COMPLETE) finish(flow) else syncFlow()
            }
        }
    }

    fun pinDelete() {
        val flow = state.value.pinFlow ?: return
        if (flow.busy) return
        if (flow.stage == PinStage.CURRENT) currentEntry.delete() else newEntry.delete()
        syncFlow()
    }

    private fun checkCurrentPin(flow: PinFlowUi) {
        val pin = currentEntry.value()
        currentEntry.reset()
        updateFlow { it.copy(entered = 0, busy = true, error = null) }
        launchSafely(onFailure = { pin.fill(0) }) {
            if (!lockSettings.verifyPin(pin.copyOf())) {
                pin.fill(0)
                if (++wrongPins >= MAX_WRONG_PINS) {
                    wrongPins = 0
                    cancelPinFlow()
                    session.lock()
                } else {
                    updateFlow { it.copy(busy = false, error = PinError.WRONG_PIN) }
                }
                return@launchSafely
            }
            wrongPins = 0
            if (flow.purpose == PinPurpose.REMOVE_DURESS) {
                if (lockSettings.removeDuress(pin)) {
                    done(SecurityMessage.DURESS_REMOVED)
                } else {
                    updateFlow { it.copy(busy = false, error = PinError.WRONG_PIN) }
                }
            } else {
                currentPin = pin
                updateFlow { it.copy(stage = PinStage.NEW, busy = false) }
            }
        }
    }

    private fun finish(flow: PinFlowUi) {
        val newPin = newEntry.value()
        newEntry.reset()
        updateFlow { it.copy(entered = 0, busy = true, error = null) }
        launchSafely(onFailure = { newPin.fill(0) }) {
            when (flow.purpose) {
                PinPurpose.CHANGE_PIN -> pinChanged(lockSettings.changePin(requireCurrentPin(), newPin), SecurityMessage.PIN_CHANGED)
                PinPurpose.SET_DURESS -> pinChanged(lockSettings.setDuress(requireCurrentPin(), newPin, flow.duressMode), SecurityMessage.DURESS_SET)
                PinPurpose.DISGUISE_CODE -> if (lockSettings.verifyPin(newPin.copyOf())) {
                    // The calculator shows what's typed; the PIN must never be the code.
                    newPin.fill(0)
                    updateFlow { it.copy(stage = PinStage.NEW, busy = false, error = PinError.CODE_IS_PIN) }
                } else {
                    lockSettings.enableDisguise(newPin)
                    done(SecurityMessage.DISGUISE_ON)
                }
                PinPurpose.REMOVE_DURESS -> Unit
            }
        }
    }

    private suspend fun pinChanged(result: PinChangeResult, message: SecurityMessage) {
        when (result) {
            PinChangeResult.CHANGED -> done(message)
            PinChangeResult.SAME_AS_OTHER_PIN -> updateFlow { it.copy(stage = PinStage.NEW, busy = false, error = PinError.SAME_AS_OTHER_PIN) }
            // The current PIN was verified a moment ago; start the prompt again to be safe.
            PinChangeResult.WRONG_PIN -> {
                wipeCurrentPin()
                updateFlow { it.copy(stage = PinStage.CURRENT, busy = false, error = PinError.WRONG_PIN) }
            }
        }
    }

    /** A copy for a [LockSettings] call, which wipes what it's given. */
    private fun requireCurrentPin(): ByteArray = checkNotNull(currentPin) { "No verified PIN" }.copyOf()

    private suspend fun done(message: SecurityMessage) {
        resetEntries()
        reload()
        state.update { it.copy(pinFlow = null, message = message) }
    }

    private suspend fun reload() {
        val lock = lockSettings.state()
        val autoLock = settings.get(SettingKeys.AUTO_LOCK)
        val faceDown = settings.get(SettingKeys.FACE_DOWN_LOCK)
        val trigger = settings.get(SettingKeys.PANIC_TRIGGER)
        state.update {
            it.copy(
                loaded = true,
                keyStorage = lock.keyStorage,
                kdfMemoryMiB = lock.kdf.memLimitMebibytes,
                autoLock = autoLock,
                biometricAvailable = BiometricAuth.isAvailable(context),
                biometricEnabled = lock.biometricEnabled,
                biometricAllowed = lock.biometricAllowed,
                duressMode = lock.duressMode,
                wipeAfterFailures = lock.wipeAfterFailures,
                disguiseEnabled = lock.disguiseEnabled,
                faceDownLock = faceDown,
                panicTrigger = trigger,
            )
        }
    }

    private fun syncFlow() = updateFlow {
        val entry = if (it.stage == PinStage.CURRENT) currentEntry else newEntry
        it.copy(
            stage = when {
                it.stage == PinStage.CURRENT || it.stage == PinStage.CHOOSE_MODE || it.stage == PinStage.INTRO -> it.stage
                newEntry.stage == PinEntry.Stage.CONFIRM -> PinStage.CONFIRM
                else -> PinStage.NEW
            },
            entered = entry.entered,
            error = when {
                entry.mismatch -> PinError.MISMATCH
                entry.entered > 0 -> null
                else -> it.error
            },
        )
    }

    private fun updateFlow(change: (PinFlowUi) -> PinFlowUi) = state.update { current ->
        current.pinFlow?.let { current.copy(pinFlow = change(it)) } ?: current
    }

    private fun resetEntries() {
        currentEntry.reset()
        newEntry.reset()
        wipeCurrentPin()
    }

    private fun wipeCurrentPin() {
        currentPin?.fill(0)
        currentPin = null
    }

    private fun launchSafely(onFailure: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Security setting not changed" }
                onFailure()
                updateFlow { it.copy(busy = false, entered = 0, error = PinError.FAILED) }
            }
        }
    }

    override fun onCleared() = resetEntries()

    private companion object {
        const val MAX_WRONG_PINS = 3
    }
}

/** A biometric prompt to run before [LockSettings.enableBiometric]; [cipher] is null in the decoy. */
class BiometricEnrolment(val cipher: Cipher?)

@HiltViewModel
class NetworkSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    private val showBridges = MutableStateFlow(false)

    val uiState: StateFlow<NetworkSettingsUiState> = combine(
        combine(NetworkToggle.entries.map { toggle -> settings.observe(toggle.key).map { toggle to it } }) { it.toMap() },
        settings.observe(SettingKeys.BRIDGE_MODE),
        showBridges,
    ) { toggles, bridgeMode, bridges ->
        NetworkSettingsUiState(toggles, bridgeMode, FakeSettingsData.networkPreview, bridges)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        NetworkSettingsUiState(
            toggles = NetworkToggle.entries.associateWith { it.key.default },
            bridgeMode = SettingKeys.BRIDGE_MODE.default,
            preview = FakeSettingsData.networkPreview,
        ),
    )

    fun onToggle(toggle: NetworkToggle, enabled: Boolean) = save { settings.set(toggle.key, enabled) }

    fun showBridges(show: Boolean) {
        showBridges.value = show
    }

    fun onBridgeSelect(mode: BridgeMode) {
        showBridges.value = false
        save { settings.set(SettingKeys.BRIDGE_MODE, mode) }
    }

    private fun save(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
            } catch (e: Exception) {
                // Locked in the meantime: the screen is going away with the database.
                TflLog.w(TAG, e) { "Network setting not saved" }
            }
        }
    }
}

private val NetworkToggle.key: SettingKey<Boolean>
    get() = when (this) {
        NetworkToggle.TOR -> SettingKeys.TOR_ENABLED
        NetworkToggle.NEARBY -> SettingKeys.NEARBY_ENABLED
        NetworkToggle.RELAY -> SettingKeys.RELAY_ENABLED
        NetworkToggle.BATTERY_SAVER -> SettingKeys.BATTERY_SAVER
        NetworkToggle.PAUSE_RELAY_ON_LOW_BATTERY -> SettingKeys.PAUSE_RELAY_ON_LOW_BATTERY
    }
