package app.tfl.feature.onboarding.finish

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.common.log.TflLog
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingValue
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.session.AppSession
import app.tfl.core.session.IdentityTools
import app.tfl.core.session.LockSettings
import app.tfl.core.session.PinEntry
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.feature.onboarding.setup.PinUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

enum class PhraseStage { WORDS, QUIZ }

data class FinishUiState(
    val restored: Boolean = false,
    /** The recovery phrase, only while its step is showing. */
    val phrase: List<String> = emptyList(),
    val phraseHidden: Boolean = false,
    val writtenDown: Boolean = false,
    val phraseStage: PhraseStage = PhraseStage.WORDS,
    /** 0-based phrase positions the quiz asks for, ascending. */
    val quizPositions: List<Int> = emptyList(),
    /** 1-based number of the first quiz word that didn't match. */
    val quizWrong: Int? = null,
    val nearbyEnabled: Boolean = SettingKeys.NEARBY_ENABLED.default,
    val torEnabled: Boolean = SettingKeys.TOR_ENABLED.default,
    val bridgeMode: BridgeMode = SettingKeys.BRIDGE_MODE.default,
    val disguiseWanted: Boolean = false,
    val disguiseCode: PinUi = PinUi(),
    /** The code typed was the app PIN, which the calculator would show to anyone watching. */
    val disguiseCodeIsPin: Boolean = false,
    /** Onboarding asked for fingerprint unlock and it hasn't been enrolled yet. */
    val biometricPending: Boolean = false,
    /** Enrolment was cancelled or failed; shown once as a notice. */
    val biometricNotice: Boolean = false,
    val busy: Boolean = false,
)

/** Onboarding after the commit point. Each step is saved, so an interrupted onboarding resumes. */
@HiltViewModel
class FinishViewModel @Inject constructor(
    private val session: AppSession,
    private val lockSettings: LockSettings,
    private val settings: SettingsRepository,
    private val tools: IdentityTools,
) : ViewModel() {

    val quizWords: List<TextFieldState> = List(QUIZ_WORDS) { TextFieldState() }

    private val disguiseEntry = PinEntry()

    private val state = MutableStateFlow(FinishUiState())
    val uiState: StateFlow<FinishUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val restored = session.wasRestored()
                val nearby = settings.get(SettingKeys.NEARBY_ENABLED)
                val tor = settings.get(SettingKeys.TOR_ENABLED)
                val bridge = settings.get(SettingKeys.BRIDGE_MODE)
                val biometric = settings.get(SettingKeys.BIOMETRIC_REQUESTED)
                state.update {
                    it.copy(restored = restored, nearbyEnabled = nearby, torEnabled = tor, bridgeMode = bridge, biometricPending = biometric)
                }
            } catch (e: Exception) {
                // Locked in the meantime; the gate shows the lock screen and this resumes after unlock.
                TflLog.w(TAG, e) { "Onboarding state unavailable" }
            }
        }
    }

    /** Called with the step the gate shows. Loads the phrase for its step and drops it after. */
    fun onStep(step: OnboardingStep) {
        if (step != OnboardingStep.RECOVERY_PHRASE) {
            forgetPhrase()
            return
        }
        if (state.value.phrase.isNotEmpty()) return
        viewModelScope.launch {
            val words = try {
                session.recoveryPhrase()
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Recovery phrase unavailable" }
                return@launch
            }
            state.update { it.copy(phrase = words, phraseStage = PhraseStage.WORDS) }
        }
    }

    fun togglePhraseHidden() = state.update { it.copy(phraseHidden = !it.phraseHidden) }

    fun setWrittenDown(written: Boolean) = state.update { it.copy(writtenDown = written) }

    fun startQuiz() {
        val current = state.value
        if (!current.writtenDown || current.phrase.size != PHRASE_WORDS) return
        quizWords.forEach { it.clearText() }
        val positions = (0 until PHRASE_WORDS).shuffled().take(QUIZ_WORDS).sorted()
        state.update { it.copy(phraseStage = PhraseStage.QUIZ, quizPositions = positions, quizWrong = null) }
    }

    fun showPhraseAgain() = state.update { it.copy(phraseStage = PhraseStage.WORDS, quizWrong = null) }

    fun suggestions(prefix: String): List<String> = tools.suggestions(prefix)

    fun isWord(word: String): Boolean = tools.isWord(word)

    fun submitQuiz() {
        val current = state.value
        if (current.phraseStage != PhraseStage.QUIZ || current.busy) return
        val wrong = current.quizPositions.withIndex().firstOrNull { (index, position) ->
            quizWords[index].text.toString().trim().lowercase() != current.phrase[position]
        }
        if (wrong != null) {
            state.update { it.copy(quizWrong = wrong.value + 1) }
            return
        }
        advance(OnboardingStep.PERMISSIONS)
    }

    fun continueFromPermissions() = advance(OnboardingStep.NETWORK)

    fun setNearby(enabled: Boolean) = state.update { it.copy(nearbyEnabled = enabled) }

    fun setTor(enabled: Boolean) = state.update { it.copy(torEnabled = enabled) }

    fun setBridge(mode: BridgeMode) = state.update { it.copy(bridgeMode = mode) }

    fun continueFromNetwork() {
        val current = state.value
        advance(OnboardingStep.DISGUISE) {
            settings.setAll(
                SettingValue(SettingKeys.NEARBY_ENABLED, current.nearbyEnabled),
                SettingValue(SettingKeys.TOR_ENABLED, current.torEnabled),
                SettingValue(SettingKeys.BRIDGE_MODE, current.bridgeMode),
            )
            true
        }
    }

    fun setDisguiseWanted(wanted: Boolean) {
        if (!wanted) disguiseEntry.reset()
        state.update { it.copy(disguiseWanted = wanted) }
        syncDisguise()
    }

    fun disguiseDigit(digit: Int) {
        disguiseEntry.digit(digit)
        state.update { it.copy(disguiseCodeIsPin = false) }
        syncDisguise()
    }

    fun disguiseDelete() {
        disguiseEntry.delete()
        syncDisguise()
    }

    fun resetDisguiseCode() {
        disguiseEntry.reset()
        syncDisguise()
    }

    fun finish() {
        val wanted = state.value.disguiseWanted
        if (wanted && disguiseEntry.stage != PinEntry.Stage.COMPLETE) return
        advance(OnboardingStep.DONE) {
            if (!wanted) return@advance true
            val code = disguiseEntry.value()
            disguiseEntry.reset()
            // One Argon2id run: the calculator shows what's typed, so the code must not be the PIN.
            if (lockSettings.verifyPin(code.copyOf())) {
                code.fill(0)
                state.update { it.copy(disguiseCodeIsPin = true) }
                syncDisguise()
                false
            } else {
                lockSettings.enableDisguise(code)
                true
            }
        }
    }

    /** Back within the steps that allow it; false when the system should handle back. */
    fun back(step: OnboardingStep): Boolean {
        when {
            state.value.busy -> Unit
            step == OnboardingStep.RECOVERY_PHRASE && state.value.phraseStage == PhraseStage.QUIZ -> showPhraseAgain()
            step == OnboardingStep.NETWORK -> advance(OnboardingStep.PERMISSIONS)
            // Typing the secret code: back cancels the disguise rather than leaving the step.
            step == OnboardingStep.DISGUISE && state.value.disguiseWanted && disguiseEntry.stage != PinEntry.Stage.COMPLETE ->
                setDisguiseWanted(false)
            step == OnboardingStep.DISGUISE -> advance(OnboardingStep.NETWORK)
            // The phrase is never shown again once the quiz is passed, so there's no way back to it.
            else -> return false
        }
        return true
    }

    /** Cipher for the enrolment prompt, or null if the key can't be created (no fingerprint enrolled, …). */
    suspend fun biometricEnrolCipher(): Cipher? = try {
        lockSettings.biometricEnrolCipher()
    } catch (e: Exception) {
        TflLog.w(TAG, e) { "Biometric key unavailable" }
        null
    }

    fun onBiometricOutcome(outcome: BiometricOutcome) {
        viewModelScope.launch {
            val enabled = try {
                if (outcome is BiometricOutcome.Success) lockSettings.enableBiometric(outcome.cipher)
                outcome is BiometricOutcome.Success
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Biometric enrolment failed" }
                false
            }
            try {
                // Asked once: after a cancel or failure, Settings → Security is the place to retry.
                settings.set(SettingKeys.BIOMETRIC_REQUESTED, false)
            } catch (e: Exception) {
                TflLog.w(TAG, e) { "Biometric request not cleared" }
            }
            state.update { it.copy(biometricPending = false, biometricNotice = !enabled) }
        }
    }

    fun biometricNoticeShown() = state.update { it.copy(biometricNotice = false) }

    /** Saves the move to [next] once [before] (if given) agrees. */
    private fun advance(next: OnboardingStep, before: suspend () -> Boolean = { true }) {
        if (state.value.busy) return
        state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                if (before()) session.advanceOnboarding(next)
            } catch (e: Exception) {
                // Stays on this step so it can be tried again.
                TflLog.w(TAG, e) { "Onboarding step not saved" }
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    private fun forgetPhrase() {
        quizWords.forEach { it.clearText() }
        state.update {
            it.copy(phrase = emptyList(), phraseHidden = false, writtenDown = false, phraseStage = PhraseStage.WORDS, quizPositions = emptyList(), quizWrong = null)
        }
    }

    private fun syncDisguise() = state.update {
        it.copy(disguiseCode = PinUi(disguiseEntry.stage, disguiseEntry.entered, disguiseEntry.mismatch))
    }

    override fun onCleared() {
        disguiseEntry.reset()
        forgetPhrase()
    }

    private companion object {
        const val TAG = "Onboarding"
        const val PHRASE_WORDS = 24
        const val QUIZ_WORDS = 3
    }
}
