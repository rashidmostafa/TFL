package app.tfl.feature.onboarding

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.PinEntry
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.feature.onboarding.finish.DisguiseScreen
import app.tfl.feature.onboarding.finish.FinishUiState
import app.tfl.feature.onboarding.finish.NetworkScreen
import app.tfl.feature.onboarding.finish.PermissionsScreen
import app.tfl.feature.onboarding.finish.PhraseQuizScreen
import app.tfl.feature.onboarding.finish.PhraseStage
import app.tfl.feature.onboarding.finish.RecoveryPhraseScreen
import app.tfl.feature.onboarding.lock.KeysUnavailableScreen
import app.tfl.feature.onboarding.lock.LockScreen
import app.tfl.feature.onboarding.lock.LockStatus
import app.tfl.feature.onboarding.lock.LockUiState
import app.tfl.feature.onboarding.setup.AppLockSetupScreen
import app.tfl.feature.onboarding.setup.DuressSetupScreen
import app.tfl.feature.onboarding.setup.IdentityScreen
import app.tfl.feature.onboarding.setup.PinUi
import app.tfl.feature.onboarding.setup.RestoreError
import app.tfl.feature.onboarding.setup.RestoreScreen
import app.tfl.feature.onboarding.setup.SetupStep
import app.tfl.feature.onboarding.setup.SetupUiState
import app.tfl.feature.onboarding.setup.WelcomeScreen
import app.tfl.feature.onboarding.stealth.CalculatorEngine.PLUS
import app.tfl.feature.onboarding.stealth.CalculatorEngine.TIMES
import app.tfl.feature.onboarding.stealth.CalculatorScreen
import app.tfl.feature.onboarding.stealth.CalculatorUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every onboarding, lock and calculator screen, recorded as golden images and checked for behaviour. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class OnboardingScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fingerprint = "F162BE7D2746777DB5A0FF5D6329EC0E"

    /** The phrase for the seed 00 01 … 1f (standard BIP39 encoding), so the golden shows real words. */
    private val phrase = (
        "abandon amount liar amount expire adjust cage candy arch gather drum bullet absurd math era live " +
            "bid rhythm alien crouch range attend journey unaware"
        ).split(" ")

    private val clicks = mutableListOf<String>()

    private fun show(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { TflTestSurface(content) }
        composeRule.captureScreenshot("onboarding_$name")
    }

    @Test
    fun welcome() {
        show("welcome") { WelcomeScreen(onCreate = { clicks += "create" }, onRestore = { clicks += "restore" }) }
        composeRule.onNodeWithText("Create identity").performClick()
        composeRule.onNodeWithText("Restore from 24-word recovery phrase").performClick()
        assertEquals(listOf("create", "restore"), clicks)
    }

    @Test
    fun identity() = show("identity") {
        IdentityScreen(
            state = SetupUiState(fingerprint = fingerprint),
            displayName = TextFieldState("Valkyrie-7"),
            onRegenerate = {},
            onContinue = {},
            onBack = {},
        )
    }

    @Test
    fun restore() = show("restore") {
        val words = List(24) { index -> TextFieldState(if (index < 6) phrase[index] else "") }
        words[6].edit { append("abs") }
        RestoreScreen(
            state = SetupUiState(step = SetupStep.RESTORE, restoreError = RestoreError.UnknownWord(7)),
            words = words,
            suggestions = { listOf("absent", "absorb", "abstract", "absurd") },
            isWord = { it in phrase },
            onSubmit = {},
            onBack = {},
        )
    }

    @Test
    fun appLock() = show("app_lock") {
        AppLockSetupScreen(
            state = SetupUiState(pin = PinUi(entered = 4), biometricAvailable = true, biometricWanted = true),
            onDigit = {}, onDelete = {}, onReset = {}, onBiometric = {}, onAutoLock = {}, onContinue = {}, onBack = {},
        )
    }

    @Test
    fun appLockDone() = show("app_lock_done") {
        AppLockSetupScreen(
            state = SetupUiState(pin = PinUi(stage = PinEntry.Stage.COMPLETE), biometricAvailable = true, biometricWanted = true),
            onDigit = {}, onDelete = {}, onReset = {}, onBiometric = {}, onAutoLock = {}, onContinue = {}, onBack = {},
        )
    }

    @Test
    fun duress() = show("duress") {
        DuressSetupScreen(
            state = SetupUiState(duressMode = DuressMode.DECOY),
            onStartPin = {}, onDigit = {}, onDelete = {}, onMode = {}, onConfirm = {}, onSkip = {}, onBack = {},
        )
    }

    @Test
    fun duressPin() = show("duress_pin") {
        DuressSetupScreen(
            state = SetupUiState(duressMode = DuressMode.WIPE, duressEntering = true, duressPin = PinUi(stage = PinEntry.Stage.CONFIRM, entered = 2)),
            onStartPin = {}, onDigit = {}, onDelete = {}, onMode = {}, onConfirm = {}, onSkip = {}, onBack = {},
        )
    }

    @Test
    fun recoveryPhrase() = show("recovery_phrase") {
        RecoveryPhraseScreen(FinishUiState(phrase = phrase, writtenDown = true), onToggleHidden = {}, onWrittenDown = {}, onVerify = {})
    }

    @Test
    fun quiz() = show("quiz") {
        PhraseQuizScreen(
            state = FinishUiState(phrase = phrase, phraseStage = PhraseStage.QUIZ, quizPositions = listOf(2, 11, 19), quizWrong = 12),
            words = listOf(TextFieldState("liar"), TextFieldState("bullet"), TextFieldState("")),
            suggestions = { emptyList() },
            isWord = { true },
            onSubmit = {},
            onShowAgain = {},
        )
    }

    @Test
    fun permissions() = show("permissions") {
        PermissionsScreen(FinishUiState(), onContinue = {}, showLocation = true)
    }

    @Test
    fun network() = show("network") {
        NetworkScreen(FinishUiState(bridgeMode = BridgeMode.OBFS4), onNearby = {}, onTor = {}, onBridge = {}, onContinue = {}, onBack = {})
    }

    @Test
    fun disguise() = show("disguise") {
        DisguiseScreen(
            FinishUiState(disguiseWanted = true, disguiseCode = PinUi(entered = 3)),
            onWanted = {}, onDigit = {}, onDelete = {}, onResetCode = {}, onFinish = {}, onBack = {},
        )
    }

    @Test
    fun disguiseReady() = show("disguise_ready") {
        DisguiseScreen(
            FinishUiState(disguiseWanted = true, disguiseCode = PinUi(stage = PinEntry.Stage.COMPLETE)),
            onWanted = {}, onDigit = {}, onDelete = {}, onResetCode = {}, onFinish = {}, onBack = {},
        )
    }

    @Test
    fun disguisePreviewCalculates() {
        composeRule.setContent {
            TflTestSurface {
                DisguiseScreen(FinishUiState(), onWanted = {}, onDigit = {}, onDelete = {}, onResetCode = {}, onFinish = {}, onBack = {})
            }
        }
        composeRule.onNodeWithText("Try the calculator").performClick()
        composeRule.onNodeWithText("7").performClick()
        composeRule.onNodeWithText("$TIMES").performClick()
        composeRule.onNodeWithText("6").performClick()
        composeRule.onNodeWithText("=").performClick()
        composeRule.onNodeWithText("42").assertExists()
    }

    @Test
    fun lock() = show("lock") {
        LockScreen(
            LockUiState(entered = 0, status = LockStatus.WrongPin(attemptsBeforeWipe = 2), biometricEnabled = true),
            onDigit = {}, onDelete = {}, onBiometric = {}, onForgot = {},
        )
    }

    @Test
    fun lockWarnsAboutAWipeOnlyWhenFewAttemptsRemain() {
        var state by mutableStateOf(LockUiState(status = LockStatus.WrongPin(attemptsBeforeWipe = 4)))
        composeRule.setContent {
            TflTestSurface { LockScreen(state, onDigit = {}, onDelete = {}, onBiometric = {}, onForgot = {}) }
        }
        composeRule.onNodeWithText("Wrong PIN").assertExists()
        state = LockUiState(status = LockStatus.WrongPin(attemptsBeforeWipe = 3))
        composeRule.onNodeWithText("Wrong PIN. 3 attempts left before TFL wipes this phone.").assertExists()
    }

    @Test
    fun lockWaiting() = show("lock_waiting") {
        LockScreen(LockUiState(status = LockStatus.Waiting(299_000)), onDigit = {}, onDelete = {}, onBiometric = {}, onForgot = {})
    }

    @Test
    fun keysUnavailable() = show("keys_unavailable") { KeysUnavailableScreen(onWipe = {}) }

    @Test
    fun calculator() = show("calculator") {
        CalculatorScreen(
            CalculatorUiState(expression = "2048${TIMES}16${PLUS}1", result = "32,769"),
            onKey = {},
            onEquals = {},
        )
    }

    @Test
    fun autoLockChoices() {
        var chosen: AutoLockTimeout? = null
        composeRule.setContent {
            TflTestSurface {
                AppLockSetupScreen(
                    state = SetupUiState(pin = PinUi(stage = PinEntry.Stage.COMPLETE)),
                    onDigit = {}, onDelete = {}, onReset = {}, onBiometric = {}, onAutoLock = { chosen = it }, onContinue = {}, onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("5 minutes").performClick()
        assertEquals(AutoLockTimeout.MINUTES_5, chosen)
    }
}
