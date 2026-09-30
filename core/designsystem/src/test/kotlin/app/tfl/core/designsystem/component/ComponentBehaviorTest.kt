package app.tfl.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.model.Transport
import app.tfl.core.testing.TflTestSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentBehaviorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transportChip_readsTransportAndDetail() {
        composeRule.setContent {
            TflTestSurface {
                Column { Transport.entries.forEach { TransportChip(it, detail = "2 hops") } }
            }
        }
        for (label in listOf("Nearby", "Mesh", "Tor", "Queued")) {
            composeRule.onNodeWithContentDescription("$label, 2 hops").assertExists()
        }
    }

    @Test
    fun transportChip_withoutDetail_readsTransportOnly() {
        composeRule.setContent { TflTestSurface { TransportChip(Transport.TOR) } }
        composeRule.onNodeWithContentDescription("Tor").assertExists()
    }

    @Test
    fun secureBadge_defaultLabels() {
        composeRule.setContent {
            TflTestSurface {
                Column {
                    SecureBadge(state = SecureBadgeState.Verified)
                    SecureBadge(state = SecureBadgeState.Unverified)
                    SecureBadge(state = SecureBadgeState.KeyChanged)
                }
            }
        }
        composeRule.onNodeWithText("E2EE").assertExists()
        composeRule.onNodeWithText("Unverified").assertExists()
        composeRule.onNodeWithText("Key changed").assertExists()
    }

    @Test
    fun buttons_click_and_disabledButtonsDoNot() {
        val clicks = mutableListOf<String>()
        composeRule.setContent {
            TflTestSurface {
                Column {
                    PrimaryButton("Primary", onClick = { clicks += "primary" })
                    GhostButton("Ghost", onClick = { clicks += "ghost" })
                    DestructiveButton("Wipe", onClick = { clicks += "wipe" })
                    PrimaryButton("Disabled", onClick = { clicks += "disabled" }, enabled = false)
                }
            }
        }
        composeRule.onNodeWithText("Primary").performClick()
        composeRule.onNodeWithText("Ghost").performClick()
        composeRule.onNodeWithText("Wipe").performClick()
        composeRule.onNodeWithText("Disabled").assertIsNotEnabled().performClick()
        assertEquals(listOf("primary", "ghost", "wipe"), clicks)
    }

    @Test
    fun iconButton_isAnnouncedByItsDescription() {
        var clicked = false
        composeRule.setContent { TflTestSurface { TflBackButton(onClick = { clicked = true }) } }
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun decorativeIcon_isHiddenFromAccessibility() {
        composeRule.setContent { TflTestSurface { TflIcon(MaterialSymbols.Search, contentDescription = null) } }
        composeRule.onNodeWithText(MaterialSymbols.Search).assertDoesNotExist()
    }

    @Test
    fun listRow_showsTextAndClicks() {
        var clicked = false
        composeRule.setContent {
            TflTestSurface {
                ListRow(
                    title = "Security",
                    subtitle = "App lock, duress PIN",
                    icon = MaterialSymbols.Security,
                    onClick = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithText("App lock, duress PIN").assertExists()
        composeRule.onNodeWithText("Security").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun filterChip_reportsSelection() {
        composeRule.setContent {
            TflTestSurface {
                Column {
                    TflFilterChip("All", selected = true, onClick = {}, count = 7)
                    TflFilterChip("Groups", selected = false, onClick = {})
                }
            }
        }
        composeRule.onNodeWithText("All", substring = true).assertIsSelected()
        composeRule.onNodeWithText("Groups").assertIsNotSelected()
    }

    @Test
    fun emptyState_actionClicks() {
        var clicked = false
        composeRule.setContent {
            TflTestSurface {
                EmptyState(
                    icon = MaterialSymbols.Forum,
                    title = "No chats",
                    message = "Add a friend in person to start.",
                    action = { GhostButton("Add friend", onClick = { clicked = true }) },
                )
            }
        }
        composeRule.onNodeWithText("No chats").assertExists()
        composeRule.onNodeWithText("Add friend").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun fingerprint_isSpelledOutForTalkBack() {
        composeRule.setContent { TflTestSurface { FingerprintBlock(listOf("7F4B", "889C")) } }
        composeRule.onNodeWithContentDescription("7 F 4 B, 8 8 9 C").assertExists()
    }

    @Test
    fun segmentedTabs_areTabsAndSelectOnClick() {
        composeRule.setContent {
            TflTestSurface {
                var selected by remember { mutableIntStateOf(0) }
                SegmentedTabs(listOf(SegmentedTab("My QR"), SegmentedTab("Scan")), selected, onSelect = { selected = it })
            }
        }
        composeRule.onNodeWithText("My QR").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        composeRule.onNodeWithText("Scan").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("Scan").assertIsSelected()
        composeRule.onNodeWithText("My QR").assertIsNotSelected()
    }

    @Test
    fun safetyNumber_isReadDigitByDigit() {
        composeRule.setContent { TflTestSurface { SafetyNumberGrid(listOf("01953", "97817")) } }
        composeRule.onNodeWithContentDescription("0 1 9 5 3, 9 7 8 1 7").assertExists()
    }

    @Test
    fun qrMatrix_mustBeSquare() {
        assertThrows(IllegalArgumentException::class.java) { QrMatrix(3, BooleanArray(8)) }
        val matrix = QrMatrix(2, booleanArrayOf(true, false, false, true))
        assertEquals(listOf(true, false, false, true), listOf(matrix.isDark(0, 0), matrix.isDark(1, 0), matrix.isDark(0, 1), matrix.isDark(1, 1)))
    }
}
