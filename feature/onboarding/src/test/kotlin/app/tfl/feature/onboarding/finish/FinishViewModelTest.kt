package app.tfl.feature.onboarding.finish

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.GateState
import app.tfl.core.session.OnboardingDraft
import app.tfl.core.session.PinEntry
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.PIN
import app.tfl.core.testing.typeText
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinishViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SessionFixture(ApplicationProvider.getApplicationContext())
    private val seed = ByteArray(32) { it.toByte() }
    private val phrase = fixture.tools.encodePhrase(seed)

    private val step get() = (fixture.session.state.value as GateState.Unlocked).onboardingStep

    @After
    fun tearDown() = fixture.database.close()

    private suspend fun commit(restored: Boolean = false, biometric: Boolean = false, at: OnboardingStep? = null): FinishViewModel {
        fixture.session.commitOnboarding(
            OnboardingDraft(
                seed = seed.copyOf(),
                restored = restored,
                displayName = "Valkyrie-7",
                pin = PIN.encodeToByteArray(),
                duressPin = null,
                duressMode = DuressMode.NONE,
                autoLock = AutoLockTimeout.IMMEDIATELY,
                wantsBiometric = biometric,
            ),
        )
        at?.let { fixture.session.advanceOnboarding(it) }
        return FinishViewModel(fixture.session, fixture.lockSettings, fixture.settings, fixture.tools).also { it.onStep(step) }
    }

    private fun FinishViewModel.typeCode(code: String) = code.forEach { disguiseDigit(it.digitToInt()) }

    @Test
    fun `the phrase step shows this identity's 24 words`() = runTest {
        val viewModel = commit()
        assertEquals(phrase, viewModel.uiState.value.phrase)
        assertFalse(viewModel.uiState.value.restored)
    }

    @Test
    fun `the quiz needs the written-down box and asks for three different words`() = runTest {
        val viewModel = commit()
        viewModel.startQuiz()
        assertEquals(PhraseStage.WORDS, viewModel.uiState.value.phraseStage)

        viewModel.setWrittenDown(true)
        viewModel.startQuiz()
        val positions = viewModel.uiState.value.quizPositions
        assertEquals(PhraseStage.QUIZ, viewModel.uiState.value.phraseStage)
        assertEquals(3, positions.toSet().size)
        assertEquals(positions.sorted(), positions)
        assertTrue(positions.all { it in 0..23 })
    }

    @Test
    fun `a wrong quiz word is pointed out and the phrase step stays`() = runTest {
        val viewModel = commit()
        viewModel.setWrittenDown(true)
        viewModel.startQuiz()
        val positions = viewModel.uiState.value.quizPositions
        positions.forEachIndexed { index, position -> viewModel.quizWords[index].typeText(phrase[position]) }
        viewModel.quizWords[1].typeText(phrase[positions[1]] + "s")
        viewModel.submitQuiz()
        assertEquals(positions[1] + 1, viewModel.uiState.value.quizWrong)
        assertEquals(OnboardingStep.RECOVERY_PHRASE, step)
    }

    @Test
    fun `the right words finish the phrase step, which then forgets the phrase`() = runTest {
        val viewModel = commit()
        viewModel.setWrittenDown(true)
        viewModel.startQuiz()
        viewModel.uiState.value.quizPositions.forEachIndexed { index, position ->
            viewModel.quizWords[index].typeText(" ${phrase[position].uppercase()} ")
        }
        viewModel.submitQuiz()
        assertEquals(OnboardingStep.PERMISSIONS, step)

        viewModel.onStep(step)
        assertTrue(viewModel.uiState.value.phrase.isEmpty())
        assertTrue(viewModel.quizWords.all { it.text.isEmpty() })
    }

    @Test
    fun `a restored identity has no phrase to show`() = runTest {
        val viewModel = commit(restored = true)
        assertEquals(OnboardingStep.PERMISSIONS, step)
        assertTrue(viewModel.uiState.value.restored)
        assertTrue(viewModel.uiState.value.phrase.isEmpty())
        viewModel.continueFromPermissions()
        assertEquals(OnboardingStep.NETWORK, step)
    }

    @Test
    fun `network choices are saved`() = runTest {
        val viewModel = commit(at = OnboardingStep.NETWORK)
        viewModel.setNearby(false)
        viewModel.setBridge(BridgeMode.SNOWFLAKE)
        viewModel.setTor(false)
        viewModel.continueFromNetwork()
        assertEquals(OnboardingStep.DISGUISE, step)
        assertFalse(fixture.settings.get(SettingKeys.NEARBY_ENABLED))
        assertFalse(fixture.settings.get(SettingKeys.TOR_ENABLED))
        assertEquals(BridgeMode.SNOWFLAKE, fixture.settings.get(SettingKeys.BRIDGE_MODE))
    }

    @Test
    fun `the disguise needs its code confirmed, then finishing turns it on`() = runTest {
        val viewModel = commit(at = OnboardingStep.DISGUISE)
        viewModel.setDisguiseWanted(true)
        viewModel.finish()
        assertEquals(OnboardingStep.DISGUISE, step)

        viewModel.typeCode("314159")
        viewModel.typeCode("314158")
        assertTrue(viewModel.uiState.value.disguiseCode.mismatch)
        viewModel.typeCode("314159")
        viewModel.typeCode("314159")
        assertEquals(PinEntry.Stage.COMPLETE, viewModel.uiState.value.disguiseCode.stage)
        viewModel.finish()

        assertEquals(OnboardingStep.DONE, step)
        assertTrue(fixture.disguise.isEnabled())
        assertTrue(fixture.disguise.matches("314159".encodeToByteArray()))
        assertFalse(fixture.disguise.matches("314158".encodeToByteArray()))
    }

    @Test
    fun `the app PIN can't be the disguise code`() = runTest {
        val viewModel = commit(at = OnboardingStep.DISGUISE)
        viewModel.setDisguiseWanted(true)
        viewModel.typeCode(PIN)
        viewModel.typeCode(PIN)
        viewModel.finish()
        assertEquals(OnboardingStep.DISGUISE, step)
        assertTrue(viewModel.uiState.value.disguiseCodeIsPin)
        assertEquals(PinEntry.Stage.ENTER, viewModel.uiState.value.disguiseCode.stage)
        assertFalse(fixture.disguise.isEnabled())
        viewModel.disguiseDigit(1)
        assertFalse(viewModel.uiState.value.disguiseCodeIsPin)
    }

    @Test
    fun `finishing without the disguise keeps the TFL icon`() = runTest {
        val viewModel = commit(at = OnboardingStep.DISGUISE)
        viewModel.setDisguiseWanted(true)
        viewModel.typeCode("314")
        viewModel.setDisguiseWanted(false)
        assertEquals(0, viewModel.uiState.value.disguiseCode.entered)
        viewModel.finish()
        assertEquals(OnboardingStep.DONE, step)
        assertFalse(fixture.disguise.isEnabled())
    }

    @Test
    fun `back while typing the secret code cancels the disguise and stays on the step`() = runTest {
        val viewModel = commit(at = OnboardingStep.DISGUISE)
        viewModel.setDisguiseWanted(true)
        viewModel.typeCode("31")
        assertTrue(viewModel.back(OnboardingStep.DISGUISE))
        assertFalse(viewModel.uiState.value.disguiseWanted)
        assertEquals(0, viewModel.uiState.value.disguiseCode.entered)
        assertEquals(OnboardingStep.DISGUISE, step)
    }

    @Test
    fun `back leaves the quiz and steps between the later screens, never back to the phrase`() = runTest {
        val viewModel = commit()
        viewModel.setWrittenDown(true)
        viewModel.startQuiz()
        assertTrue(viewModel.back(OnboardingStep.RECOVERY_PHRASE))
        assertEquals(PhraseStage.WORDS, viewModel.uiState.value.phraseStage)
        assertFalse(viewModel.back(OnboardingStep.RECOVERY_PHRASE))

        fixture.session.advanceOnboarding(OnboardingStep.DISGUISE)
        assertTrue(viewModel.back(OnboardingStep.DISGUISE))
        assertEquals(OnboardingStep.NETWORK, step)
        assertTrue(viewModel.back(OnboardingStep.NETWORK))
        assertEquals(OnboardingStep.PERMISSIONS, step)
        assertFalse(viewModel.back(OnboardingStep.PERMISSIONS))
    }

    @Test
    fun `a cancelled fingerprint enrolment isn't asked again and leaves a notice`() = runTest {
        val viewModel = commit(biometric = true)
        assertTrue(viewModel.uiState.value.biometricPending)
        viewModel.onBiometricOutcome(BiometricOutcome.Cancelled)
        assertFalse(viewModel.uiState.value.biometricPending)
        assertTrue(viewModel.uiState.value.biometricNotice)
        assertFalse(fixture.settings.get(SettingKeys.BIOMETRIC_REQUESTED))
        viewModel.biometricNoticeShown()
        assertFalse(viewModel.uiState.value.biometricNotice)
    }
}
