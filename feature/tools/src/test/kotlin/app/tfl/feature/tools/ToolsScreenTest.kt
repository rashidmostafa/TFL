package app.tfl.feature.tools

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.feature.tools.fake.FakeToolsData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class ToolsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var placeholderTaps = 0

    private fun setContent() {
        composeRule.setContent {
            TflTestSurface {
                ToolsContent(FakeToolsData.state, onOpenProfile = {}, onNotYetAvailable = { placeholderTaps++ })
            }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("tools_home")
    }

    @Test
    fun toolsAndActions_reportPlaceholder() {
        setContent()
        composeRule.onNodeWithText("Shared notes").performClick()
        composeRule.onNodeWithText("View all").performClick()
        composeRule.onNodeWithText("New shared item").performClick()
        assertEquals(3, placeholderTaps)
    }
}
