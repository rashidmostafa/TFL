package app.tfl.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.feature.settings.fake.FakeSettingsData
import app.tfl.feature.settings.network.NetworkSettingsContent
import app.tfl.feature.settings.security.SecuritySettingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
                    uiState = FakeSettingsData.settings,
                    appVersion = "0.1.0",
                    onShowIdentity = { opened += "identity" },
                    onOpenSecurity = { opened += "security" },
                    onOpenNetwork = { opened += "network" },
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
        composeRule.onNodeWithText("Security").performClick()
        composeRule.onNodeWithText("Network & transports").performClick()
        composeRule.onNodeWithText("Privacy").performClick()
        assertEquals(listOf("identity", "security", "network", "placeholder"), opened)
    }

    @Test
    fun aboutRow_showsAppVersion() {
        setContent()
        composeRule.onNodeWithText("TFL 0.1.0", substring = true).assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class SecuritySettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewModel = SecuritySettingsViewModel()

    private fun setContent() {
        composeRule.setContent {
            TflTestSurface {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                SecuritySettingsContent(
                    uiState = uiState,
                    onBack = {},
                    onToggle = viewModel::onToggle,
                    onPanicTriggerSelect = viewModel::onPanicTriggerSelect,
                    onNotYetAvailable = {},
                )
            }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("security_settings")
    }

    @Test
    fun tappingARow_flipsItsSwitch() {
        setContent()
        composeRule.onNodeWithText("App lock").assertIsOn().performClick()
        composeRule.onNodeWithText("App lock").assertIsOff()
    }

    @Test
    fun tappingATrigger_selectsIt() {
        setContent()
        composeRule.onNodeWithText("Hold volume down for six seconds").performClick().assertIsSelected()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class NetworkSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewModel = NetworkSettingsViewModel()

    @Test
    fun screenshot() {
        composeRule.setContent {
            TflTestSurface {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                NetworkSettingsContent(uiState, onBack = {}, onToggle = viewModel::onToggle, onNotYetAvailable = {})
            }
        }
        composeRule.captureScreenshot("network_settings")
    }
}
