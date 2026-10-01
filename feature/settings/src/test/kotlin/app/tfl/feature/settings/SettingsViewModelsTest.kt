package app.tfl.feature.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.PanicTrigger
import app.tfl.core.model.security.Profile
import app.tfl.core.session.UnlockOutcome
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.crypto.FAST_KDF
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.DURESS_PIN
import app.tfl.core.testing.session.SessionFixture.Companion.PIN
import app.tfl.core.transport.NearbyReadiness
import app.tfl.core.transport.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = SessionFixture(context)
    private val session = fixture.session
    private val seed = ByteArray(32) { it.toByte() }

    @After
    fun tearDown() = fixture.database.close()

    private suspend fun unlocked() = session.commitOnboarding(fixture.draft(seed.copyOf()))

    private fun security() = SecuritySettingsViewModel(context, session, fixture.lockSettings, fixture.settings)

    private val SecuritySettingsViewModel.state get() = uiState.value

    private fun SecuritySettingsViewModel.type(pin: String) = pin.forEach { pinDigit(it.digitToInt()) }

    @Test
    fun `settings show the real identity and lock facts`() = runTest {
        unlocked()
        val viewModel = SettingsViewModel(fixture.identities, fixture.settings, fixture.lockSettings)
        val state = viewModel.uiState.first { it.fingerprint.isNotEmpty() && it.keyStorage != null }
        assertEquals("Valkyrie-7", state.displayName)
        assertEquals(fixture.tools.fingerprintOf(seed).chunked(4), state.fingerprint)
        assertEquals(8, state.fingerprint.size)
        assertEquals(KeyStorageLevel.TEE, state.keyStorage)
        assertEquals(FAST_KDF.memLimitMebibytes, state.kdfMemoryMiB)
        assertEquals(AutoLockTimeout.SECONDS_30, state.autoLock)
    }

    @Test
    fun `the security screen loads the real lock configuration`() = runTest {
        unlocked()
        val state = security().state
        assertTrue(state.loaded)
        assertEquals(KeyStorageLevel.TEE, state.keyStorage)
        assertEquals(DuressMode.NONE, state.duressMode)
        assertTrue(state.biometricAllowed)
        assertFalse(state.disguiseEnabled)
        assertEquals(
            listOf(Protection.PIN_LOCK, Protection.ENCRYPTED_STORAGE, Protection.SCREENSHOT_BLOCKING),
            state.activeProtections,
        )
    }

    @Test
    fun `changing the PIN checks the current one first`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.startPinFlow(PinPurpose.CHANGE_PIN)
        assertEquals(PinStage.CURRENT, viewModel.state.pinFlow?.stage)
        viewModel.type("000000")
        assertEquals(PinError.WRONG_PIN, viewModel.state.pinFlow?.error)
        viewModel.type(PIN)
        assertEquals(PinStage.NEW, viewModel.state.pinFlow?.stage)
        viewModel.type("111111")
        assertEquals(PinStage.CONFIRM, viewModel.state.pinFlow?.stage)
        viewModel.type("111112")
        assertEquals(PinError.MISMATCH, viewModel.state.pinFlow?.error)
        viewModel.type("111111")
        viewModel.type("111111")
        assertNull(viewModel.state.pinFlow)
        assertEquals(SecurityMessage.PIN_CHANGED, viewModel.state.message)

        session.lock()
        assertTrue(session.unlock(PIN.encodeToByteArray()) is UnlockOutcome.WrongPin)
        assertEquals(UnlockOutcome.Unlocked, session.unlock("111111".encodeToByteArray()))
    }

    @Test
    fun `three wrong current PINs in a row lock TFL`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.startPinFlow(PinPurpose.CHANGE_PIN)
        viewModel.type("000000")
        viewModel.type("000001")
        assertTrue(session.isUnlocked)
        viewModel.type("000002")
        assertFalse(session.isUnlocked)
        assertNull(viewModel.state.pinFlow)
    }

    @Test
    fun `a decoy duress PIN is set up after choosing the mode, and turns fingerprint unlock off`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.onBiometricEnrolled(BiometricOutcome.Success(checkNotNull(viewModel.biometricEnrolment()?.cipher)))
        assertTrue(viewModel.state.biometricEnabled)

        viewModel.startPinFlow(PinPurpose.SET_DURESS)
        assertEquals(PinStage.CHOOSE_MODE, viewModel.state.pinFlow?.stage)
        viewModel.chooseDuressMode(DuressMode.DECOY)
        viewModel.proceed()
        viewModel.type(PIN)
        viewModel.type(PIN)
        viewModel.type(PIN)
        assertEquals(PinError.SAME_AS_OTHER_PIN, viewModel.state.pinFlow?.error)
        assertEquals(PinStage.NEW, viewModel.state.pinFlow?.stage)
        viewModel.type(DURESS_PIN)
        viewModel.type(DURESS_PIN)

        assertEquals(SecurityMessage.DURESS_SET, viewModel.state.message)
        assertEquals(DuressMode.DECOY, viewModel.state.duressMode)
        assertFalse(viewModel.state.biometricEnabled)
        assertFalse(viewModel.state.biometricAllowed)
        assertTrue(Protection.DURESS_PIN in viewModel.state.activeProtections)

        session.lock()
        assertEquals(UnlockOutcome.Unlocked, session.unlock(DURESS_PIN.encodeToByteArray()))
        assertEquals(Profile.DECOY, session.profile)
    }

    @Test
    fun `removing the duress PIN needs the PIN`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.startPinFlow(PinPurpose.SET_DURESS)
        viewModel.chooseDuressMode(DuressMode.WIPE)
        viewModel.proceed()
        viewModel.type(PIN)
        viewModel.type(DURESS_PIN)
        viewModel.type(DURESS_PIN)
        assertEquals(DuressMode.WIPE, viewModel.state.duressMode)

        viewModel.startPinFlow(PinPurpose.REMOVE_DURESS)
        viewModel.type(PIN)
        assertEquals(SecurityMessage.DURESS_REMOVED, viewModel.state.message)
        assertEquals(DuressMode.NONE, viewModel.state.duressMode)
        assertTrue(viewModel.state.biometricAllowed)
    }

    @Test
    fun `the disguise refuses the PIN as its code`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.setDisguise(true)
        // First how the disguise works (TFL closes once it's on), then the code.
        assertEquals(PinStage.INTRO, viewModel.state.pinFlow?.stage)
        viewModel.proceed()
        assertEquals(PinStage.NEW, viewModel.state.pinFlow?.stage)
        viewModel.type(PIN)
        viewModel.type(PIN)
        assertEquals(PinError.CODE_IS_PIN, viewModel.state.pinFlow?.error)
        assertFalse(fixture.disguise.isEnabled())

        viewModel.type("314159")
        viewModel.type("314159")
        assertEquals(SecurityMessage.DISGUISE_ON, viewModel.state.message)
        assertTrue(viewModel.state.disguiseEnabled)
        assertTrue(fixture.disguise.matches("314159".encodeToByteArray()))

        viewModel.setDisguise(false)
        assertFalse(viewModel.state.disguiseEnabled)
        assertFalse(fixture.disguise.isEnabled())
    }

    @Test
    fun `cancelling a prompt changes nothing`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.startPinFlow(PinPurpose.CHANGE_PIN)
        viewModel.type(PIN)
        viewModel.type("111")
        viewModel.cancelPinFlow()
        assertNull(viewModel.state.pinFlow)
        session.lock()
        assertEquals(UnlockOutcome.Unlocked, session.unlock(PIN.encodeToByteArray()))
    }

    @Test
    fun `auto-lock, wipe-after, face-down and panic trigger are saved`() = runTest {
        unlocked()
        val viewModel = security()
        viewModel.showSheet(SecuritySheet.AUTO_LOCK)
        viewModel.setAutoLock(AutoLockTimeout.MINUTES_5)
        assertNull(viewModel.state.sheet)
        viewModel.setWipeAfterFailures(10)
        viewModel.setFaceDownLock(true)
        viewModel.setPanicTrigger(PanicTrigger.VOLUME_DOWN)

        assertEquals(AutoLockTimeout.MINUTES_5, session.autoLock)
        assertEquals(AutoLockTimeout.MINUTES_5, fixture.settings.get(SettingKeys.AUTO_LOCK))
        assertEquals(10, fixture.vault.status().wipeAfterFailures)
        assertTrue(fixture.settings.get(SettingKeys.FACE_DOWN_LOCK))
        assertEquals(PanicTrigger.VOLUME_DOWN, fixture.settings.get(SettingKeys.PANIC_TRIGGER))

        val reopened = security().state
        assertEquals(AutoLockTimeout.MINUTES_5, reopened.autoLock)
        assertEquals(10, reopened.wipeAfterFailures)
        assertTrue(reopened.faceDownLock)
        assertEquals(PanicTrigger.VOLUME_DOWN, reopened.panicTrigger)
        assertTrue(Protection.WIPE_AFTER_FAILURES in reopened.activeProtections)
    }

    @Test
    fun `in the decoy, lock settings look real but never reach the real lock state`() = runTest {
        unlocked()
        security().apply {
            startPinFlow(PinPurpose.SET_DURESS)
            proceed()
            type(PIN)
            type(DURESS_PIN)
            type(DURESS_PIN)
        }
        session.lock()
        session.unlock(DURESS_PIN.encodeToByteArray())

        val decoy = security()
        assertEquals(DuressMode.NONE, decoy.state.duressMode)
        decoy.setWipeAfterFailures(5)
        assertEquals(5, decoy.state.wipeAfterFailures)
        assertEquals(0, fixture.vault.status().wipeAfterFailures)
        assertEquals(DuressMode.DECOY, fixture.vault.status().duressMode)
    }

    @Test
    fun `a failed fingerprint prompt leaves it off and says so`() = runTest {
        unlocked()
        val viewModel = security()
        assertNotNull(viewModel.biometricEnrolment())
        viewModel.onBiometricEnrolled(BiometricOutcome.Failed("Too many attempts"))
        assertEquals(SecurityMessage.BIOMETRIC_FAILED, viewModel.state.message)
        assertFalse(viewModel.state.biometricEnabled)
        viewModel.messageShown()
        assertNull(viewModel.state.message)
    }

    private class FakeTransportStatus : TransportStatus {
        override val reachable = MutableStateFlow<Set<Long>>(emptySet())
        override val running = MutableStateFlow(false)
    }

    private class FakeReadiness : NearbyReadiness {
        override val permitted = MutableStateFlow(true)
        override val ready = MutableStateFlow(true)
    }

    @Test
    fun `Nearby shows off, needing setup, or the friends it's linked with`() = runTest {
        unlocked()
        val transport = FakeTransportStatus()
        val readiness = FakeReadiness()
        val viewModel = NetworkSettingsViewModel(fixture.settings, transport, readiness)

        fixture.settings.set(SettingKeys.NEARBY_ENABLED, false)
        assertEquals(NearbyCardState.Off, viewModel.uiState.first { it.nearby == NearbyCardState.Off }.nearby)
        fixture.settings.set(SettingKeys.NEARBY_ENABLED, true)
        readiness.ready.value = false
        val needsSetup = viewModel.uiState.first { it.nearby == NearbyCardState.NeedsSetup }
        assertFalse(needsSetup.nearbyReady)
        readiness.ready.value = true
        transport.reachable.value = setOf(4L, 9L)
        assertTrue(viewModel.uiState.first { it.nearby == NearbyCardState.Running(2) }.nearbyReady)

        viewModel.onToggle(NetworkToggle.STAY_REACHABLE, false)
        assertFalse(viewModel.uiState.first { !it.isOn(NetworkToggle.STAY_REACHABLE) }.isOn(NetworkToggle.STAY_REACHABLE))
        assertFalse(fixture.settings.get(SettingKeys.STAY_REACHABLE))
    }

    @Test
    fun `network choices are saved`() = runTest {
        unlocked()
        val viewModel = NetworkSettingsViewModel(fixture.settings, FakeTransportStatus(), FakeReadiness())
        viewModel.onToggle(NetworkToggle.RELAY, false)
        viewModel.onToggle(NetworkToggle.BATTERY_SAVER, true)
        viewModel.showBridges(true)
        viewModel.onBridgeSelect(BridgeMode.OBFS4)

        val state = viewModel.uiState.first { it.bridgeMode == BridgeMode.OBFS4 }
        assertFalse(state.isOn(NetworkToggle.RELAY))
        assertTrue(state.isOn(NetworkToggle.BATTERY_SAVER))
        assertTrue(state.isOn(NetworkToggle.TOR))
        assertFalse(state.showBridges)
        assertFalse(fixture.settings.get(SettingKeys.RELAY_ENABLED))
        assertEquals(BridgeMode.OBFS4, fixture.settings.get(SettingKeys.BRIDGE_MODE))
    }
}
