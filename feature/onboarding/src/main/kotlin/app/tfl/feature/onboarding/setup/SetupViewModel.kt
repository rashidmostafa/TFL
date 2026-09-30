package app.tfl.feature.onboarding.setup

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.crypto.phrase.PhraseResult
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.AppSession
import app.tfl.core.session.IdentityTools
import app.tfl.core.session.OnboardingDraft
import app.tfl.core.session.PinEntry
import app.tfl.core.session.biometric.BiometricAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { WELCOME, IDENTITY, RESTORE, APP_LOCK, DURESS, COMMITTING }

/** Progress of a PIN being typed (never the digits themselves). */
data class PinUi(val stage: PinEntry.Stage = PinEntry.Stage.ENTER, val entered: Int = 0, val mismatch: Boolean = false)

sealed interface RestoreError {
    /** [number] is 1-based, as shown on screen. */
    data class UnknownWord(val number: Int) : RestoreError
    data object Checksum : RestoreError
    data object Incomplete : RestoreError
}

data class SetupUiState(
    val step: SetupStep = SetupStep.WELCOME,
    val restored: Boolean = false,
    val fingerprint: String = "",
    val nameError: Boolean = false,
    val restoreError: RestoreError? = null,
    val pin: PinUi = PinUi(),
    val biometricAvailable: Boolean = false,
    val biometricWanted: Boolean = false,
    val autoLock: AutoLockTimeout = AutoLockTimeout.IMMEDIATELY,
    val duressMode: DuressMode = DuressMode.DECOY,
    /** The duress step's second part: typing the duress PIN (after choosing what it does). */
    val duressEntering: Boolean = false,
    val duressPin: PinUi = PinUi(),
    val duressSameAsPin: Boolean = false,
    val commitFailed: Boolean = false,
)

/**
 * Onboarding up to the commit point. Secrets (seed, PINs) live only here, in byte arrays, until
 * [AppSession.commitOnboarding] seals them; they're wiped right after, or when this is cleared.
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val session: AppSession,
    private val tools: IdentityTools,
) : ViewModel() {

    val displayName = TextFieldState()
    val restoreWords: List<TextFieldState> = List(WORDS) { TextFieldState() }

    private var seed: ByteArray? = null
    private val pinEntry = PinEntry()
    private val duressEntry = PinEntry()

    private val state = MutableStateFlow(SetupUiState(biometricAvailable = BiometricAuth.isAvailable(context)))
    val uiState: StateFlow<SetupUiState> = state.asStateFlow()

    fun createIdentity() {
        replaceSeed(tools.newSeed())
        state.update { it.copy(step = SetupStep.IDENTITY, restored = false, nameError = false) }
    }

    /** A new seed (new keys and fingerprint). Only before the identity is saved, and not for a restore. */
    fun regenerate() {
        if (!state.value.restored) replaceSeed(tools.newSeed())
    }

    fun startRestore() = state.update { it.copy(step = SetupStep.RESTORE, restoreError = null) }

    fun suggestions(prefix: String): List<String> = tools.suggestions(prefix)

    fun isWord(word: String): Boolean = tools.isWord(word)

    fun submitRestore() {
        val words = restoreWords.map { it.text.toString() }
        if (words.any { it.isBlank() }) {
            state.update { it.copy(restoreError = RestoreError.Incomplete) }
            return
        }
        when (val result = tools.decodePhrase(words)) {
            is PhraseResult.Valid -> {
                replaceSeed(result.seed)
                state.update { it.copy(step = SetupStep.IDENTITY, restored = true, restoreError = null, nameError = false) }
            }
            is PhraseResult.UnknownWord -> state.update { it.copy(restoreError = RestoreError.UnknownWord(result.position + 1)) }
            PhraseResult.ChecksumMismatch -> state.update { it.copy(restoreError = RestoreError.Checksum) }
            is PhraseResult.WrongWordCount -> state.update { it.copy(restoreError = RestoreError.Incomplete) }
        }
    }

    fun continueFromIdentity() {
        val name = displayName.text.trim()
        if (name.length !in 1..MAX_NAME) {
            state.update { it.copy(nameError = true) }
            return
        }
        state.update { it.copy(step = SetupStep.APP_LOCK, nameError = false) }
    }

    fun pinDigit(digit: Int) {
        pinEntry.digit(digit)
        syncPin()
    }

    fun pinDelete() {
        pinEntry.delete()
        syncPin()
    }

    fun resetPin() {
        pinEntry.reset()
        syncPin()
    }

    fun setBiometric(wanted: Boolean) = state.update { it.copy(biometricWanted = wanted && it.biometricAvailable) }

    fun setAutoLock(timeout: AutoLockTimeout) = state.update { it.copy(autoLock = timeout) }

    fun continueFromAppLock() {
        if (pinEntry.stage == PinEntry.Stage.COMPLETE) state.update { it.copy(step = SetupStep.DURESS) }
    }

    fun setDuressMode(mode: DuressMode) = state.update { it.copy(duressMode = mode) }

    fun startDuressPin() = state.update { it.copy(duressEntering = true) }

    fun duressDigit(digit: Int) {
        duressEntry.digit(digit)
        val sameAsPin = duressEntry.sameAs(pinEntry)
        if (sameAsPin) duressEntry.reset()
        state.update { it.copy(duressSameAsPin = sameAsPin) }
        syncPin()
    }

    fun duressDelete() {
        duressEntry.delete()
        syncPin()
    }

    fun confirmDuress() {
        if (duressEntry.stage == PinEntry.Stage.COMPLETE) commit(withDuress = true)
    }

    fun skipDuress() {
        duressEntry.reset()
        commit(withDuress = false)
    }

    fun retryCommit() = commit(withDuress = duressEntry.stage == PinEntry.Stage.COMPLETE)

    /** Returns false when there's no earlier step (the system should handle back). */
    fun back(): Boolean {
        when (state.value.step) {
            SetupStep.WELCOME -> return false
            SetupStep.RESTORE -> state.update { it.copy(step = SetupStep.WELCOME, restoreError = null) }
            SetupStep.IDENTITY -> {
                wipeSeed()
                state.update { it.copy(step = if (it.restored) SetupStep.RESTORE else SetupStep.WELCOME, fingerprint = "") }
            }
            SetupStep.APP_LOCK -> {
                pinEntry.reset()
                state.update { it.copy(step = SetupStep.IDENTITY) }
                syncPin()
            }
            SetupStep.DURESS -> {
                duressEntry.reset()
                state.update {
                    // From the PIN back to the choices, or from the choices back to the app PIN.
                    if (it.duressEntering) it.copy(duressEntering = false, duressSameAsPin = false)
                    else it.copy(step = SetupStep.APP_LOCK, duressSameAsPin = false)
                }
                syncPin()
            }
            SetupStep.COMMITTING -> Unit
        }
        return true
    }

    private fun commit(withDuress: Boolean) {
        val currentSeed = seed ?: return
        val current = state.value
        state.update { it.copy(step = SetupStep.COMMITTING, commitFailed = false) }
        val draft = OnboardingDraft(
            seed = currentSeed.copyOf(),
            restored = current.restored,
            displayName = displayName.text.trim().toString(),
            pin = pinEntry.value(),
            duressPin = if (withDuress) duressEntry.value() else null,
            duressMode = if (withDuress) current.duressMode else DuressMode.NONE,
            autoLock = current.autoLock,
            wantsBiometric = current.biometricWanted,
        )
        viewModelScope.launch {
            try {
                session.commitOnboarding(draft)
                wipeEverything()
            } catch (e: Exception) {
                state.update { it.copy(commitFailed = true) }
            }
        }
    }

    private fun replaceSeed(newSeed: ByteArray) {
        wipeSeed()
        seed = newSeed
        state.update { it.copy(fingerprint = tools.fingerprintOf(newSeed)) }
    }

    private fun wipeSeed() {
        seed?.let(tools::wipe)
        seed = null
    }

    private fun syncPin() = state.update {
        it.copy(
            pin = PinUi(pinEntry.stage, pinEntry.entered, pinEntry.mismatch),
            duressPin = PinUi(duressEntry.stage, duressEntry.entered, duressEntry.mismatch),
        )
    }

    private fun wipeEverything() {
        wipeSeed()
        pinEntry.reset()
        duressEntry.reset()
        displayName.clearText()
        restoreWords.forEach { it.clearText() }
    }

    override fun onCleared() = wipeEverything()

    private companion object {
        const val WORDS = 24
        const val MAX_NAME = 32
    }
}
