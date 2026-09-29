package app.tfl.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import app.tfl.core.testing.MainDispatcherRule
import app.tfl.core.testing.SCREENSHOT_DEVICE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.core.testing.typeText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = MapViewModel()

    @Test
    fun `lists every friend sharing a location`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(listOf("elena", "alex", "maya"), state.friends.map { it.id })
            assertEquals(3, state.sharingCount)
        }
    }

    @Test
    fun `search matches names and places`() = runTest {
        viewModel.uiState.test {
            awaitItem()
            viewModel.searchQuery.typeText("ridge")
            assertEquals(listOf("alex"), awaitItem().friends.map { it.id })
            viewModel.searchQuery.typeText("zzz")
            val empty = awaitItem()
            assertTrue(empty.friends.isEmpty())
            assertTrue(empty.hasQuery)
            assertEquals(3, empty.sharingCount)
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE)
class MapScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewModel = MapViewModel()
    private var placeholderTaps = 0

    private fun setContent() {
        composeRule.setContent {
            TflTestSurface {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MapContent(uiState, viewModel.searchQuery, onOpenProfile = {}, onNotYetAvailable = { placeholderTaps++ })
            }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("map")
    }

    @Test
    fun unbuiltActions_reportPlaceholder() {
        setContent()
        composeRule.onNodeWithText("Drop meetup pin").performClick()
        composeRule.onNodeWithText("Nearest").performClick()
        assertEquals(2, placeholderTaps)
    }
}
