package app.tfl.feature.onboarding.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.common.log.TflLog
import app.tfl.core.session.AppSession
import app.tfl.core.session.UnlockOutcome
import app.tfl.core.session.biometric.BiometricOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

sealed interface LockStatus {
    data object Idle : LockStatus
    data object Checking : LockStatus

    /** [attemptsBeforeWipe] is null when wipe-after-N is off. */
    data class WrongPin(val attemptsBeforeWipe: Int?) : LockStatus

    data class Waiting(val remainingMillis: Long) : LockStatus
    data object Failed : LockStatus
}

data class LockUiState(
    val entered: Int = 0,
    val status: LockStatus = LockStatus.Idle,
    val biometricEnabled: Boolean = false,
    /** Asks for the fingerprint once each time the lock screen appears. */
    val biometricAutoPrompt: Boolean = false,
    val forgotDialog: Boolean = false,
) {
    val canType: Boolean get() = status !is LockStatus.Checking && status !is LockStatus.Waiting
}

/**
 * The lock screen. The PIN is collected into a byte array and handed to [AppSession.unlock], which
 * wipes it; it never becomes a String.
 */
@HiltViewModel
class LockViewModel @Inject constructor(private val session: AppSession) : ViewModel() {

    private val pin = ByteArray(PIN_LENGTH)
    private var entered = 0
    private var countdown: Job? = null

    private val state = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = state.asStateFlow()

    /** The lock screen appeared (cold start, or auto-lock): start fresh and read the lock status. */
    fun onShown() {
        clearPin()
        state.update { LockUiState(status = if (it.status is LockStatus.Waiting) it.status else LockStatus.Idle) }
        viewModelScope.launch {
            val info = session.lockScreenInfo() ?: return@launch
            state.update { it.copy(biometricEnabled = info.biometricEnabled, biometricAutoPrompt = info.biometricEnabled) }
            if (info.retryAfterMillis > 0) startCountdown(info.retryAfterMillis)
        }
    }

    fun digit(value: Int) {
        if (!state.value.canType || entered == PIN_LENGTH) return
        pin[entered++] = ('0'.code + value).toByte()
        state.update { it.copy(entered = entered, status = if (it.status is LockStatus.Failed) LockStatus.Idle else it.status) }
        if (entered == PIN_LENGTH) submit()
    }

    fun delete() {
        if (!state.value.canType || entered == 0) return
        pin[--entered] = 0
        state.update { it.copy(entered = entered) }
    }

    private fun submit() {
        val attempt = pin.copyOf()
        state.update { it.copy(status = LockStatus.Checking) }
        viewModelScope.launch {
            val outcome = try {
                session.unlock(attempt)
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Unlock failed" }
                UnlockOutcome.Failed
            }
            clearPin()
            when (outcome) {
                UnlockOutcome.Unlocked, UnlockOutcome.Wiped -> state.update { it.copy(entered = 0, status = LockStatus.Idle) }
                is UnlockOutcome.WrongPin -> {
                    state.update { it.copy(entered = 0, status = LockStatus.WrongPin(outcome.attemptsBeforeWipe)) }
                    if (outcome.retryAfterMillis > 0) startCountdown(outcome.retryAfterMillis, outcome.attemptsBeforeWipe)
                }
                is UnlockOutcome.Throttled -> {
                    state.update { it.copy(entered = 0) }
                    startCountdown(outcome.retryAfterMillis)
                }
                UnlockOutcome.Failed -> state.update { it.copy(entered = 0, status = LockStatus.Failed) }
            }
        }
    }

    /** The Keystore cipher for BiometricPrompt, or null if fingerprint unlock is off or was invalidated. */
    suspend fun biometricCipher(): Cipher? {
        state.update { it.copy(biometricAutoPrompt = false) }
        return session.biometricUnlockCipher().also { cipher ->
            if (cipher == null) state.update { it.copy(biometricEnabled = false) }
        }
    }

    fun onBiometricOutcome(outcome: BiometricOutcome) {
        val cipher = (outcome as? BiometricOutcome.Success)?.cipher ?: return
        viewModelScope.launch {
            val outcome = try {
                session.unlockWithBiometric(cipher)
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Biometric unlock failed" }
                UnlockOutcome.Failed
            }
            if (outcome == UnlockOutcome.Failed) state.update { it.copy(status = LockStatus.Failed) }
        }
    }

    fun showForgot(show: Boolean) = state.update { it.copy(forgotDialog = show) }

    /** "Forgot PIN?" and the unreadable-keys screen: delete everything and restart fresh. */
    fun wipe() {
        state.update { it.copy(forgotDialog = false) }
        viewModelScope.launch {
            try {
                session.wipe()
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Wipe failed" }
                state.update { it.copy(status = LockStatus.Failed) }
            }
        }
    }

    private fun startCountdown(millis: Long, attemptsBeforeWipe: Int? = null) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            // The vault enforces the delay; this only shows it. Ticking starts after the vault's
            // clock did, so the countdown never ends early.
            var remaining = millis
            while (remaining > 0) {
                state.update { it.copy(status = LockStatus.Waiting(remaining)) }
                val tick = remaining.coerceAtMost(TICK_MILLIS)
                delay(tick)
                remaining -= tick
            }
            state.update {
                it.copy(status = if (attemptsBeforeWipe != null) LockStatus.WrongPin(attemptsBeforeWipe) else LockStatus.Idle)
            }
        }
    }

    private fun clearPin() {
        pin.fill(0)
        entered = 0
    }

    override fun onCleared() = clearPin()

    private companion object {
        const val TAG = "Lock"
        const val PIN_LENGTH = 6
        const val TICK_MILLIS = 1_000L
    }
}
