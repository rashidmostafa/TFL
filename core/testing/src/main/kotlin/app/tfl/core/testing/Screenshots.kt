package app.tfl.core.testing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import app.tfl.core.designsystem.theme.TflTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage

/** Screenshot device: Pixel 5 (393 × 851 dp), the closest standard phone to the 390dp Stitch frames. */
const val SCREENSHOT_DEVICE = RobolectricDeviceQualifiers.Pixel5

/**
 * Same width, but tall enough to render a whole scrolling screen at once, like the full-page
 * Stitch exports in design/screens.
 */
const val SCREENSHOT_DEVICE_FULL_PAGE = "w393dp-h2000dp-xhdpi"

/** [content] in the TFL theme on the canvas colour, as it appears in the app. */
@Composable
fun TflTestSurface(content: @Composable () -> Unit) {
    TflTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = TflTheme.colors.canvas,
            contentColor = TflTheme.colors.textPrimary,
            content = content,
        )
    }
}

/**
 * Records or verifies [node] (the whole screen by default) as `src/test/screenshots/<name>.png`.
 *
 * `./gradlew recordRoborazziDebug` writes the golden images; `./gradlew verifyRoborazziDebug` fails
 * on any difference. A plain `./gradlew test` only renders them.
 */
fun ComposeContentTestRule.captureScreenshot(name: String, node: SemanticsNodeInteraction = onRoot()) {
    node.captureRoboImage("src/test/screenshots/$name.png")
}
