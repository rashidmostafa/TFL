package app.tfl.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.transport.android.NearbyPermissions
import app.tfl.core.transport.android.NearbyStatus
import app.tfl.feature.settings.nearby.NearbySetupContent
import app.tfl.feature.settings.nearby.PermissionAnswer
import app.tfl.feature.settings.notifications.NotificationSettingsContent
import app.tfl.feature.settings.notifications.NotificationSettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class NearbySetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show(status: NearbyStatus, answer: PermissionAnswer = PermissionAnswer.NOT_ASKED, sdk: Int) {
        composeRule.setContent {
            TflTestSurface {
                NearbySetupContent(
                    status = status,
                    answer = answer,
                    sdk = sdk,
                    onBack = { calls += "back" },
                    onAllow = { calls += "allow" },
                    onOpenAppSettings = { calls += "app settings" },
                    onOpenBluetooth = { calls += "bluetooth" },
                    onOpenLocation = { calls += "location" },
                )
            }
        }
    }

    private fun missing(sdk: Int) = NearbyStatus(NearbyPermissions.required(sdk), bluetoothOn = false, locationNeeded = NearbyPermissions.locationSwitchNeeded(sdk), locationOn = false)

    @Test
    fun android12_explainsNearbyDevicesAndLocationFirst() {
        show(missing(31), sdk = 31)
        composeRule.onNodeWithText("precise location", substring = true).assertExists()
        composeRule.captureScreenshot("nearby_setup")
        composeRule.onNodeWithText("Allow").performClick()
        composeRule.onNodeWithText("Bluetooth settings").performClick()
        assertEquals(listOf("allow", "bluetooth"), calls)
    }

    @Test
    fun android10_asksForLocationAndItsSwitch() {
        show(missing(29), sdk = 29)
        composeRule.onNodeWithText("Allow Location").assertExists()
        composeRule.onNodeWithText("Location settings").performClick()
        assertEquals(listOf("location"), calls)
    }

    @Test
    fun refusedTwice_sendsToSystemSettings() {
        show(missing(36), PermissionAnswer.BLOCKED, sdk = 36)
        composeRule.onNodeWithText("Android won't ask again", substring = true).assertExists()
        composeRule.captureScreenshot("nearby_setup_blocked")
        composeRule.onNodeWithText("Open settings").performClick()
        assertEquals(listOf("app settings"), calls)
    }

    @Test
    fun refusedOnce_asksAgain() {
        show(missing(36), PermissionAnswer.REFUSED, sdk = 36)
        composeRule.onNodeWithText("Not allowed", substring = true).assertExists()
        composeRule.onNodeWithText("Allow").performClick()
        assertEquals(listOf("allow"), calls)
    }

    @Test
    fun whenAllIsInPlace_itSaysSo() {
        show(NearbyStatus(emptyList(), bluetoothOn = true, locationNeeded = false, locationOn = false), sdk = 36)
        composeRule.captureScreenshot("nearby_setup_ready")
        composeRule.onNodeWithText("Nearby is ready").assertExists()
        composeRule.onNodeWithText("Done").performClick()
        assertEquals(listOf("back"), calls)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class NotificationSettingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private val fixture = SessionFixture(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = fixture.database.close()

    @Test
    fun screenshot_andSwitch() {
        val calls = mutableListOf<String>()
        composeRule.setContent {
            TflTestSurface {
                NotificationSettingsContent(
                    showSender = false,
                    allowed = false,
                    onBack = {},
                    onShowSender = { calls += "sender:$it" },
                    onOpenSystemSettings = { calls += "system" },
                )
            }
        }
        composeRule.onNodeWithText("The lock screen never shows more", substring = true).assertExists()
        composeRule.captureScreenshot("notification_settings")
        composeRule.onNodeWithText("Show sender name").performClick()
        composeRule.onNodeWithText("Notification settings").performClick()
        assertEquals(listOf("sender:true", "system"), calls)
    }

    @Test
    fun showSender_isOffByDefault_andSaved() = runTest {
        fixture.session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))
        val viewModel = NotificationSettingsViewModel(fixture.settings)
        assertEquals(false, viewModel.showSender.first())
        viewModel.setShowSender(true)
        assertTrue(viewModel.showSender.first { it })
        assertTrue(fixture.settings.get(SettingKeys.NOTIFY_SHOW_SENDER))
    }
}
