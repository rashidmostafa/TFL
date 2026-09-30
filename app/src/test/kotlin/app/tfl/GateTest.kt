package app.tfl

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.SCREENSHOT_DEVICE
import app.tfl.testing.AppStateRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What MainActivity opens on, for each state the lock gate can be in. */
abstract class GateTestBase(state: AppStateRule.State) {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val appState = AppStateRule(state)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected fun typePin(pin: String) = pin.forEach { composeRule.onNodeWithContentDescription(it.toString()).performClick() }

    protected fun waitForText(text: String) = composeRule.waitUntil(TIMEOUT) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = SCREENSHOT_DEVICE)
class NewPhoneGateTest : GateTestBase(AppStateRule.State.NEW_PHONE) {
    @Test
    fun opensOnWelcome() {
        waitForText("Create identity")
        composeRule.onNodeWithText("Restore from 24-word recovery phrase").assertExists()
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = SCREENSHOT_DEVICE)
class LockedGateTest : GateTestBase(AppStateRule.State.LOCKED) {
    @Test
    fun opensOnTheLockScreen_andThePinOpensTheApp() {
        composeRule.onNodeWithText("Enter your PIN").assertExists()
        composeRule.onNodeWithText("Emergency bulletins").assertDoesNotExist()
        typePin(AppStateRule.PIN)
        waitForText("Emergency bulletins")
    }

    @Test
    fun aWrongPinKeepsItLocked() {
        typePin("000000")
        waitForText("Wrong PIN")
        composeRule.onNodeWithText("Emergency bulletins").assertDoesNotExist()
    }
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = SCREENSHOT_DEVICE)
class InterruptedOnboardingGateTest : GateTestBase(AppStateRule.State.ONBOARDING) {
    @Test
    fun resumesAtTheRecoveryPhrase() {
        waitForText("Write down your recovery phrase")
        waitForText("abandon")
    }
}
