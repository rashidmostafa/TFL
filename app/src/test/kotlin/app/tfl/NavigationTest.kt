package app.tfl

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

/** Drives the real MainActivity: tabs, the Settings and contacts screens, back, and placeholder actions. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = SCREENSHOT_DEVICE)
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val appState = AppStateRule(AppStateRule.State.UNLOCKED)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun tab(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test
    fun startsOnChats() {
        tab("Chats").assertIsSelected()
        composeRule.onNodeWithText("Emergency bulletins").assertExists()
    }

    @Test
    fun everyTabOpensItsScreen() {
        tab("Map").performClick()
        composeRule.onNodeWithText("Active friends").assertExists()
        tab("Vault").performClick()
        composeRule.onNodeWithText("Lock vault now").assertExists()
        tab("Tools").performClick()
        composeRule.onNodeWithText("Shared workspace").assertExists()
        tab("Settings").performClick()
        composeRule.onNodeWithText("Valkyrie-7").assertExists()
        tab("Chats").performClick()
        composeRule.onNodeWithText("Emergency bulletins").assertExists()
    }

    @Test
    fun settingsDetail_hidesTabs_andBackReturns() {
        tab("Settings").performClick()
        composeRule.onNodeWithText("Security").performClick()
        composeRule.onNodeWithText("Lock when face down").assertExists()
        tab("Chats").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Valkyrie-7").assertExists()
        tab("Settings").assertIsSelected()
    }

    @Test
    fun profileButton_opensSettings() {
        composeRule.onNodeWithContentDescription("Your identity").performClick()
        composeRule.onNodeWithText("Valkyrie-7").assertExists()
    }

    @Test
    fun unbuiltAction_explainsItself() {
        composeRule.onNodeWithText("Emergency bulletins").performClick()
        composeRule.onNodeWithText("Not available yet").assertExists()
    }

    @Test
    fun scan_opensAddFriendOnTheCamera() {
        composeRule.onNodeWithText("SCAN").performClick()
        composeRule.onNodeWithText("Add friend").assertExists()
        composeRule.onNodeWithText("Camera needed to scan").assertExists()
        tab("Chats").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        tab("Chats").assertIsSelected()
    }

    @Test
    fun newChat_opensFriends_andAddFriendShowsMyCode() {
        composeRule.onNodeWithContentDescription("New chat").performClick()
        composeRule.onNodeWithText("No friends yet").assertExists()

        composeRule.onNodeWithText("Add friend").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Your pairing code. Let your friend scan it.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("New code in", substring = true).assertExists()
    }

    @Test
    fun settings_accountAndIdentity_openFriendsAndMyCode() {
        tab("Settings").performClick()
        composeRule.onNodeWithText("Account & identity").performClick()
        composeRule.onNodeWithText("No friends yet").assertExists()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Public identity").performClick()
        composeRule.onNodeWithText("Show my pairing code").performClick()
        composeRule.onNodeWithText("My QR").assertIsSelected()
    }
}
