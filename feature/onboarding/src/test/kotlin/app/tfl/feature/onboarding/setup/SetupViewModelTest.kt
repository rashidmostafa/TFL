package app.tfl.feature.onboarding.setup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.Profile
import app.tfl.core.session.GateState
import app.tfl.core.session.PinEntry
import app.tfl.core.session.UnlockOutcome
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.DURESS_PIN
import app.tfl.core.testing.session.SessionFixture.Companion.PIN
import app.tfl.core.testing.typeText
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = SessionFixture(context)
    private val viewModel = SetupViewModel(context, fixture.session, fixture.tools)
    private val state get() = viewModel.uiState.value

    /** The seed 00 01 … 1f, so its phrase is fixed. */
    private val seed = ByteArray(32) { it.toByte() }

    @After
    fun tearDown() = fixture.database.close()

    private fun typePin(pin: String) = pin.forEach { viewModel.pinDigit(it.digitToInt()) }

    private fun typeDuress(pin: String) = pin.forEach { viewModel.duressDigit(it.digitToInt()) }

    private fun toAppLock() {
        viewModel.createIdentity()
        viewModel.displayName.typeText("Valkyrie-7")
        viewModel.continueFromIdentity()
    }

    private fun toDuress() {
        toAppLock()
        typePin(PIN)
        typePin(PIN)
        viewModel.continueFromAppLock()
    }

    private fun typeRestoreWords(words: List<String>) = viewModel.restoreWords.zip(words).forEach { (field, word) -> field.typeText(word) }

    @Test
    fun `a new identity shows its fingerprint and a new key changes it`() {
        viewModel.createIdentity()
        val first = state.fingerprint
        assertEquals(SetupStep.IDENTITY, state.step)
        assertEquals(32, first.length)
        viewModel.regenerate()
        assertNotEquals(first, state.fingerprint)
    }

    @Test
    fun `a display name of 1 to 32 characters is required`() {
        viewModel.createIdentity()
        viewModel.continueFromIdentity()
        assertTrue(state.nameError)
        viewModel.displayName.typeText("   ")
        viewModel.continueFromIdentity()
        assertTrue(state.nameError)
        viewModel.displayName.typeText("x".repeat(33))
        viewModel.continueFromIdentity()
        assertTrue(state.nameError)
        viewModel.displayName.typeText("Valkyrie-7")
        viewModel.continueFromIdentity()
        assertFalse(state.nameError)
        assertEquals(SetupStep.APP_LOCK, state.step)
    }

    @Test
    fun `the PIN must be typed twice the same`() {
        toAppLock()
        typePin("123456")
        assertEquals(PinEntry.Stage.CONFIRM, state.pin.stage)
        typePin("123457")
        assertTrue(state.pin.mismatch)
        assertEquals(PinEntry.Stage.ENTER, state.pin.stage)
        viewModel.continueFromAppLock()
        assertEquals(SetupStep.APP_LOCK, state.step)

        typePin("123456")
        typePin("123456")
        assertEquals(PinEntry.Stage.COMPLETE, state.pin.stage)
        viewModel.continueFromAppLock()
        assertEquals(SetupStep.DURESS, state.step)
    }

    @Test
    fun `the duress PIN is refused as soon as it equals the app PIN`() {
        toDuress()
        typeDuress(PIN)
        assertTrue(state.duressSameAsPin)
        assertEquals(PinEntry.Stage.ENTER, state.duressPin.stage)
        assertEquals(0, state.duressPin.entered)

        typeDuress(DURESS_PIN)
        assertFalse(state.duressSameAsPin)
        typeDuress(DURESS_PIN)
        assertEquals(PinEntry.Stage.COMPLETE, state.duressPin.stage)
    }

    @Test
    fun `back from the duress PIN returns to the duress choices`() {
        toDuress()
        viewModel.setDuressMode(DuressMode.WIPE)
        viewModel.startDuressPin()
        typeDuress("135")
        assertTrue(state.duressEntering)
        assertTrue(viewModel.back())
        assertEquals(SetupStep.DURESS, state.step)
        assertFalse(state.duressEntering)
        assertEquals(0, state.duressPin.entered)
        assertEquals(DuressMode.WIPE, state.duressMode)
        assertTrue(viewModel.back())
        assertEquals(SetupStep.APP_LOCK, state.step)
    }

    @Test
    fun `restore points out missing, unknown and mistyped words`() {
        val phrase = fixture.tools.encodePhrase(seed)
        viewModel.startRestore()
        viewModel.submitRestore()
        assertEquals(RestoreError.Incomplete, state.restoreError)

        typeRestoreWords(phrase.toMutableList().apply { this[6] = "notaword" })
        viewModel.submitRestore()
        assertEquals(RestoreError.UnknownWord(7), state.restoreError)

        typeRestoreWords(phrase.toMutableList().apply { this[0] = if (this[0] == "zoo") "wrong" else "zoo" })
        viewModel.submitRestore()
        assertEquals(RestoreError.Checksum, state.restoreError)
        assertEquals(SetupStep.RESTORE, state.step)
    }

    @Test
    fun `a valid phrase restores the same identity, whatever the case and spacing`() {
        viewModel.startRestore()
        typeRestoreWords(fixture.tools.encodePhrase(seed).map { " ${it.uppercase()} " })
        viewModel.submitRestore()
        assertEquals(SetupStep.IDENTITY, state.step)
        assertTrue(state.restored)
        assertEquals(fixture.tools.fingerprintOf(seed), state.fingerprint)
    }

    @Test
    fun `skipping the duress PIN commits the identity and wipes what onboarding held`() = runTest {
        toDuress()
        viewModel.skipDuress()
        assertEquals(GateState.Unlocked(Profile.REAL, OnboardingStep.RECOVERY_PHRASE), fixture.session.state.value)
        assertEquals("Valkyrie-7", fixture.identities.get()?.displayName)
        assertEquals("", viewModel.displayName.text.toString())
        assertFalse(state.commitFailed)
    }

    @Test
    fun `a decoy duress PIN is committed with its own profile`() = runTest {
        toDuress()
        viewModel.setDuressMode(DuressMode.DECOY)
        typeDuress(DURESS_PIN)
        typeDuress(DURESS_PIN)
        viewModel.confirmDuress()
        val realFingerprint = checkNotNull(fixture.identities.get()).fingerprintHex

        fixture.session.lock()
        assertEquals(UnlockOutcome.Unlocked, fixture.session.unlock(DURESS_PIN.encodeToByteArray()))
        assertEquals(Profile.DECOY, fixture.session.profile)
        assertNotEquals(realFingerprint, checkNotNull(fixture.identities.get()).fingerprintHex)
    }

    @Test
    fun `a restored identity skips the phrase step`() = runTest {
        viewModel.startRestore()
        typeRestoreWords(fixture.tools.encodePhrase(seed))
        viewModel.submitRestore()
        viewModel.displayName.typeText("Valkyrie-7")
        viewModel.continueFromIdentity()
        typePin(PIN)
        typePin(PIN)
        viewModel.continueFromAppLock()
        viewModel.skipDuress()
        assertEquals(GateState.Unlocked(Profile.REAL, OnboardingStep.PERMISSIONS), fixture.session.state.value)
        assertEquals(fixture.tools.fingerprintOf(seed), fixture.identities.get()?.fingerprintHex)
    }

    @Test
    fun `back walks the steps in reverse and drops the seed`() {
        toDuress()
        typeDuress("13")
        assertTrue(viewModel.back())
        assertEquals(SetupStep.APP_LOCK, state.step)
        // The finished PIN is kept ("Start over" changes it); the half-typed duress PIN isn't.
        assertEquals(PinEntry.Stage.COMPLETE, state.pin.stage)
        assertEquals(0, state.duressPin.entered)
        assertTrue(viewModel.back())
        assertEquals(SetupStep.IDENTITY, state.step)
        assertEquals(PinEntry.Stage.ENTER, state.pin.stage)
        assertTrue(viewModel.back())
        assertEquals(SetupStep.WELCOME, state.step)
        assertEquals("", state.fingerprint)
        assertFalse(viewModel.back())
    }
}
