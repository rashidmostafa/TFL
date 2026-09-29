package app.tfl.feature.chats

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.core.testing.typeText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class ChatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewModel = ChatsViewModel()
    private var placeholderTaps = 0

    private fun setContent() {
        composeRule.setContent {
            TflTestSurface {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                ChatsContent(
                    uiState = uiState,
                    searchQuery = viewModel.searchQuery,
                    onFilterSelect = viewModel::onFilterSelect,
                    onOpenProfile = {},
                    onNotYetAvailable = { placeholderTaps++ },
                )
            }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("chats_home")
    }

    @Test
    fun groupsFilter_showsOnlyGroups() {
        setContent()
        composeRule.onNodeWithText("Groups", substring = true).performClick()
        composeRule.onNodeWithText("Sector 7 Friends").assertExists()
        composeRule.onNodeWithText("Dr. Maya Lin").assertDoesNotExist()
        composeRule.onNodeWithText("Emergency bulletins").assertDoesNotExist()
    }

    @Test
    fun searchWithoutMatches_showsEmptyState() {
        setContent()
        viewModel.searchQuery.typeText("nobody by this name")
        composeRule.onNodeWithText("No matches").assertExists()
    }

    @Test
    fun unbuiltActions_reportPlaceholder() {
        setContent()
        composeRule.onNodeWithText("SCAN").performClick()
        composeRule.onNodeWithText("Resolve key").performScrollTo().performClick()
        assertEquals(2, placeholderTaps)
    }
}
