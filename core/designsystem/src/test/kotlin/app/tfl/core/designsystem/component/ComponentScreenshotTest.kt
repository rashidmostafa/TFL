package app.tfl.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.designsystem.catalog.CatalogAvatars
import app.tfl.core.designsystem.catalog.CatalogBarsAndInputs
import app.tfl.core.designsystem.catalog.CatalogBubbles
import app.tfl.core.designsystem.catalog.CatalogButtons
import app.tfl.core.designsystem.catalog.CatalogCalloutsAndKeys
import app.tfl.core.designsystem.catalog.CatalogChipsAndBadges
import app.tfl.core.designsystem.catalog.CatalogEmptyState
import app.tfl.core.designsystem.catalog.CatalogListRows
import app.tfl.core.designsystem.catalog.CatalogSheetHeader
import app.tfl.core.testing.SCREENSHOT_DEVICE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One golden image per component-catalog group. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE)
class ComponentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            TflTestSurface {
                Box {
                    Box(
                        Modifier
                            .testTag(TAG)
                            .padding(16.dp),
                    ) { content() }
                }
            }
        }
        composeRule.captureScreenshot("designsystem_$name", composeRule.onNodeWithTag(TAG))
    }

    @Test
    fun chipsAndBadges() = capture("chips_badges") { CatalogChipsAndBadges() }

    @Test
    fun buttons() = capture("buttons") { CatalogButtons() }

    @Test
    fun listRows() = capture("list_rows") { CatalogListRows() }

    @Test
    fun bubbles() = capture("bubbles") { CatalogBubbles() }

    @Test
    fun avatars() = capture("avatars") { CatalogAvatars() }

    @Test
    fun barsAndInputs() = capture("bars_inputs") { CatalogBarsAndInputs() }

    @Test
    fun calloutsAndKeys() = capture("callouts_keys") { CatalogCalloutsAndKeys() }

    @Test
    fun sheetHeader() = capture("sheet") { CatalogSheetHeader() }

    @Test
    fun emptyState() = capture("empty_state") { CatalogEmptyState() }

    private companion object {
        const val TAG = "component"
    }
}
