package app.tfl.feature.onboarding.lock

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.PIN
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LockViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SessionFixture(ApplicationProvider.getApplicationContext())
    private val session = fixture.session

    @After
    fun tearDown() = fixture.database.close()

    /** An identity exists; [configure] runs while unlocked, then the app locks and shows the lock screen. */
    private suspend fun lockScreen(configure: suspend () -> Unit = {}): LockViewModel {
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))
        configure()
        session.lock()
        return LockViewModel(session).also { it.onShown() }
    }

    private fun LockViewModel.type(pin: String) = pin.forEach { digit(it.digitToInt()) }

    private val LockViewModel.status get() = uiState.value.status

    @Test
    fun `the right PIN unlocks`() = runTest {
        val viewModel = lockScreen()
        viewModel.type(PIN)
        assertTrue(session.isUnlocked)
        assertEquals(0, viewModel.uiState.value.entered)
        assertEquals(LockStatus.Idle, viewModel.status)
    }

    @Test
    fun `a wrong PIN says so and clears the dots`() = runTest {
        val viewModel = lockScreen()
        viewModel.type("000")
        assertEquals(3, viewModel.uiState.value.entered)
        viewModel.type("000")
        assertEquals(LockStatus.WrongPin(attemptsBeforeWipe = null), viewModel.status)
        assertEquals(0, viewModel.uiState.value.entered)
        assertFalse(session.isUnlocked)
    }

    @Test
    fun `the fifth wrong PIN starts a countdown that blocks typing`() = runTest {
        val viewModel = lockScreen()
        repeat(5) { viewModel.type("000000") }
        assertEquals(LockStatus.Waiting(30_000), viewModel.status)
        viewModel.type(PIN)
        assertEquals(0, viewModel.uiState.value.entered)
        assertFalse(session.isUnlocked)

        advanceTimeBy(10_001)
        assertEquals(LockStatus.Waiting(20_000), viewModel.status)
        fixture.clock.advance(30_000)
        advanceTimeBy(20_000)
        assertEquals(LockStatus.Idle, viewModel.status)
        viewModel.type(PIN)
        assertTrue(session.isUnlocked)
    }

    @Test
    fun `a delay still running when the lock screen appears is shown`() = runTest {
        val viewModel = lockScreen()
        repeat(5) { viewModel.type("000000") }
        fixture.clock.advance(10_000)
        val reopened = LockViewModel(session).also { it.onShown() }
        assertEquals(LockStatus.Waiting(20_000), reopened.status)
    }

    @Test
    fun `with wipe-after-N on, the attempts left are shown`() = runTest {
        val viewModel = lockScreen { fixture.lockSettings.setWipeAfterFailures(3) }
        viewModel.type("000000")
        assertEquals(LockStatus.WrongPin(attemptsBeforeWipe = 2), viewModel.status)
        viewModel.type("000000")
        assertEquals(LockStatus.WrongPin(attemptsBeforeWipe = 1), viewModel.status)
    }

    @Test
    fun `fingerprint unlock prompts once when the lock screen appears and unlocks`() = runTest {
        val viewModel = lockScreen {
            val cipher = checkNotNull(fixture.lockSettings.biometricEnrolCipher())
            fixture.lockSettings.enableBiometric(cipher)
        }
        assertTrue(viewModel.uiState.value.biometricEnabled)
        assertTrue(viewModel.uiState.value.biometricAutoPrompt)

        val cipher = viewModel.biometricCipher()
        assertNotNull(cipher)
        assertFalse(viewModel.uiState.value.biometricAutoPrompt)
        viewModel.onBiometricOutcome(BiometricOutcome.Cancelled)
        assertFalse(session.isUnlocked)
        viewModel.onBiometricOutcome(BiometricOutcome.Success(cipher))
        assertTrue(session.isUnlocked)
    }

    @Test
    fun `a new fingerprint enrolment turns the fingerprint key off`() = runTest {
        val viewModel = lockScreen {
            fixture.lockSettings.enableBiometric(checkNotNull(fixture.lockSettings.biometricEnrolCipher()))
        }
        fixture.keys.biometricInvalidated = true
        assertEquals(null, viewModel.biometricCipher())
        assertFalse(viewModel.uiState.value.biometricEnabled)
    }

    @Test
    fun `forgot PIN wipes everything`() = runTest {
        val viewModel = lockScreen()
        viewModel.showForgot(true)
        assertTrue(viewModel.uiState.value.forgotDialog)
        viewModel.wipe()
        assertFalse(viewModel.uiState.value.forgotDialog)
        assertEquals(1, fixture.restarts)
        assertFalse(fixture.vault.isSetUp())
    }

    @Test
    fun `waits are shown as minutes and seconds, rounded up`() {
        assertEquals("0:30", formatWait(30_000))
        assertEquals("0:01", formatWait(1))
        assertEquals("4:59", formatWait(298_001))
        assertEquals("60:00", formatWait(3_600_000))
    }
}
