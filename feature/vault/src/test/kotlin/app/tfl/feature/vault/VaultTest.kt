package app.tfl.feature.vault

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.SCREENSHOT_DEVICE_FULL_PAGE
import app.tfl.core.testing.TflTestSurface
import app.tfl.core.testing.captureScreenshot
import app.tfl.feature.vault.fake.FakeVaultData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

class GigabytesFormatTest {

    @Test
    fun `whole numbers have no decimals, others keep one`() {
        assertEquals("128", gigabytes(128f))
        assertEquals("24.8", gigabytes(24.8f))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = SCREENSHOT_DEVICE_FULL_PAGE)
class VaultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var placeholderTaps = 0

    private fun setContent() {
        composeRule.setContent {
            TflTestSurface {
                VaultContent(FakeVaultData.state, onOpenProfile = {}, onNotYetAvailable = { placeholderTaps++ })
            }
        }
    }

    @Test
    fun screenshot() {
        setContent()
        composeRule.captureScreenshot("vault_home")
    }

    @Test
    fun lockAndAreas_reportPlaceholder() {
        setContent()
        composeRule.onNodeWithText("Lock vault now").performClick()
        composeRule.onNodeWithText("342 items").performClick()
        assertEquals(2, placeholderTaps)
    }
}
