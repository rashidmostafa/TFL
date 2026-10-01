package app.tfl.feature.settings

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.PanicTrigger
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.feature.settings.fake.FakeSettingsData
import app.tfl.feature.settings.network.NetworkSettingsContent
import app.tfl.feature.settings.security.PinFlowScreen
import app.tfl.feature.settings.security.SecurityActions
import app.tfl.feature.settings.security.SecuritySettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val SAMPLE_FINGERPRINT = "F162BE7D2746777DB5A0FF5D6329EC0E".chunked(4)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun setContent() {
        composeRule.setContent {
            TflTestSurface {
                SettingsContent(
                    uiState = SettingsUiState(
                        displayName = "Valkyrie-7",
                        fingerprint = SAMPLE_FINGERPRINT,
                        keyStorage = KeyStorageLevel.STRONGBOX,
                        kdfMemoryMiB = 256,
                        autoLock = AutoLockTimeout.IMMEDIATELY,
                    ),
                    appVersion = "0.1.0",
                    onShowIdentity = { opened += "identity" },
                    onOpenSecurity = { opened += "security" },
                    onOpenNetwork = { opened += "network" },
                    onOpenNotifications = { opened += "notifications" },
                    onOpenFriends = { opened += "friends" },
                    onNotYetAvailable = { opened += "placeholder" },
                    developerSection = null,
                )
            }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("settings")
    }

    @Test
    fun rows_openTheirScreens() {
        setContent()
        composeRule.onNodeWithText("Public identity").performClick()
        composeRule.onNodeWithText("Account & identity").performClick()
        composeRule.onNodeWithText("Security").performClick()
        composeRule.onNodeWithText("Network & transports").performClick()
        composeRule.onNodeWithText("Notifications").performClick()
        composeRule.onNodeWithText("Privacy").performClick()
        composeRule.onNodeWithText("Emergency duress / panic wipe").performClick()
        assertEquals(listOf("identity", "friends", "security", "network", "notifications", "placeholder", "security"), opened)
    }

    @Test
    fun identityCard_showsTheFingerprintEnds_andTilesShowLockFacts() {
        setContent()
        composeRule.onNodeWithText("F162…EC0E · Ed25519", substring = true).assertExists()
        composeRule.onNodeWithText("StrongBox").assertExists()
        composeRule.onNodeWithText("PIN · 256 MiB").assertExists()
        composeRule.onNodeWithText("Instant").assertExists()
        composeRule.onNodeWithText("TFL 0.1.0", substring = true).assertExists()
    }
}

/** Records what the Security screen asks for. */
private class RecordingActions : SecurityActions {
    val calls = mutableListOf<String>()
    override fun openSheet(sheet: SecuritySheet?) { calls += "sheet:$sheet" }
    override fun setAutoLock(timeout: AutoLockTimeout) { calls += "autoLock:$timeout" }
    override fun setWipeAfterFailures(count: Int) { calls += "wipeAfter:$count" }
    override fun setBiometric(enabled: Boolean) { calls += "biometric:$enabled" }
    override fun setFaceDownLock(enabled: Boolean) { calls += "faceDown:$enabled" }
    override fun setPanicTrigger(trigger: PanicTrigger) { calls += "trigger:$trigger" }
    override fun setDisguise(enabled: Boolean) { calls += "disguise:$enabled" }
    override fun startPinFlow(purpose: PinPurpose) { calls += "pin:$purpose" }
    override fun notYetAvailable() { calls += "placeholder" }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class SecuritySettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val actions = RecordingActions()

    private val sample = SecuritySettingsUiState(
        loaded = true,
        keyStorage = KeyStorageLevel.STRONGBOX,
        kdfMemoryMiB = 256,
        autoLock = AutoLockTimeout.SECONDS_30,
        biometricAvailable = true,
        biometricEnabled = true,
        wipeAfterFailures = 10,
    )

    private fun setContent(state: SecuritySettingsUiState = sample) {
        composeRule.setContent {
            TflTestSurface { SecuritySettingsContent(uiState = state, onBack = {}, actions = actions) }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("security_settings")
    }

    @Test
    fun rows_startTheirPrompts() {
        setContent()
        composeRule.onNodeWithText("Change PIN").performClick()
        composeRule.onNodeWithText("Set up").performClick()
        composeRule.onNodeWithText("Auto-lock").performClick()
        composeRule.onNodeWithText("Wipe after failed attempts").performClick()
        composeRule.onNodeWithText("Calculator disguise").performClick()
        composeRule.onNodeWithText("Lock when face down").performClick()
        composeRule.onNodeWithText("Fingerprint unlock").performClick()
        assertEquals(
            listOf(
                "pin:CHANGE_PIN",
                "pin:SET_DURESS",
                "sheet:AUTO_LOCK",
                "sheet:WIPE_AFTER",
                "disguise:true",
                "faceDown:true",
                "biometric:false",
            ),
            actions.calls,
        )
    }

    @Test
    fun tappingATrigger_selectsIt() {
        setContent()
        composeRule.onNodeWithText("Hold volume down for six seconds").performClick()
        assertEquals(listOf("trigger:VOLUME_DOWN"), actions.calls)
    }

    @Test
    fun withADuressPin_fingerprintUnlockIsOff_andTheDuressPinCanBeRemoved() {
        setContent(sample.copy(duressMode = DuressMode.DECOY, biometricEnabled = false, biometricAllowed = false))
        composeRule.onNodeWithText("Fingerprint unlock").assertIsNotEnabled()
        composeRule.onNodeWithText("On · opens an empty decoy profile").assertExists()
        composeRule.onNodeWithText("Remove duress PIN").performClick()
        composeRule.onNodeWithText("5 of 6 active").assertExists()
        assertEquals(listOf("pin:REMOVE_DURESS"), actions.calls)
    }

    @Test
    fun pinPromptScreenshot() {
        composeRule.setContent {
            TflTestSurface {
                PinFlowScreen(
                    flow = PinFlowUi(PinPurpose.CHANGE_PIN, PinStage.CURRENT, entered = 0, error = PinError.WRONG_PIN),
                    onDigit = {}, onDelete = {}, onChooseMode = {}, onContinue = {}, onCancel = {},
                )
            }
        }
        composeRule.captureScreenshot("security_pin_prompt")
    }

    @Test
    fun disguiseIntroScreenshot() {
        var continued = false
        composeRule.setContent {
            TflTestSurface {
                PinFlowScreen(
                    flow = PinFlowUi(PinPurpose.DISGUISE_CODE, PinStage.INTRO),
                    onDigit = {}, onDelete = {}, onChooseMode = {}, onContinue = { continued = true }, onCancel = {},
                )
            }
        }
        composeRule.captureScreenshot("security_disguise_intro")
        composeRule.onNodeWithText("TFL closes as soon as you turn the disguise on, and locks whenever you leave it.").assertExists()
        composeRule.onNodeWithText("Choose a secret code").performClick()
        assertEquals(true, continued)
    }

    @Test
    fun withTheDisguiseOn_autoLockSaysImmediately() {
        setContent(sample.copy(disguiseEnabled = true))
        composeRule.onNodeWithText("Immediately while the calculator disguise is on").assertExists()
    }

    @Test
    fun duressModeChoice() {
        var chosen: DuressMode? = null
        composeRule.setContent {
            TflTestSurface {
                PinFlowScreen(
                    flow = PinFlowUi(PinPurpose.SET_DURESS, PinStage.CHOOSE_MODE),
                    onDigit = {}, onDelete = {}, onChooseMode = { chosen = it }, onContinue = {}, onCancel = {},
                )
            }
        }
        composeRule.captureScreenshot("security_duress_mode")
        composeRule.onNodeWithText("Wipe everything").performClick()
        assertEquals(DuressMode.WIPE, chosen)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class NetworkSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = NetworkSettingsUiState(
        toggles = NetworkToggle.entries.associateWith { it != NetworkToggle.BATTERY_SAVER },
        bridgeMode = BridgeMode.SNOWFLAKE,
        preview = FakeSettingsData.networkPreview,
        nearby = NearbyCardState.Running(friendsNearby = 2),
        nearbyReady = true,
    )

    @Test
    fun screenshot() {
        composeRule.setContent {
            TflTestSurface { NetworkSettingsContent(state, onBack = {}, onToggle = { _, _ -> }, onShowBridges = {}) }
        }
        composeRule.captureScreenshot("network_settings")
    }

    @Test
    fun screenshot_nearbyNeedsSetup() {
        val setUp = mutableListOf<String>()
        composeRule.setContent {
            TflTestSurface {
                NetworkSettingsContent(
                    state.copy(nearby = NearbyCardState.NeedsSetup, nearbyReady = false),
                    onBack = {},
                    onToggle = { _, _ -> },
                    onShowBridges = {},
                    onSetUpNearby = { setUp += "setup" },
                )
            }
        }
        composeRule.captureScreenshot("network_settings_nearby_setup")
        composeRule.onNodeWithText("Set up Nearby").performClick()
        assertEquals(listOf("setup"), setUp)
    }

    @Test
    fun switches_andTheBridgeRow_reportTaps() {
        val taps = mutableListOf<String>()
        composeRule.setContent {
            TflTestSurface {
                NetworkSettingsContent(
                    state,
                    onBack = {},
                    onToggle = { toggle, on -> taps += "$toggle:$on" },
                    onShowBridges = { taps += "bridges" },
                )
            }
        }
        composeRule.onNodeWithText("Stay reachable in the background").performClick()
        composeRule.onNodeWithText("Relay for friends").performClick()
        composeRule.onNodeWithText("Connection to Tor").performClick()
        composeRule.onNodeWithText("Snowflake bridge").assertExists()
        composeRule.onNodeWithText("Linked with 2 friends nearby.").assertExists()
        assertEquals(listOf("STAY_REACHABLE:false", "RELAY:false", "bridges"), taps)
    }
}
