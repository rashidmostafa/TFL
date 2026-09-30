package app.tfl.feature.contacts.scan

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.testing.TflTestSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The camera permission, as Android answers it: refused once, then refused for good. */
@RunWith(AndroidJUnit4::class)
class QrScannerPermissionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var requests = 0

    /** Answers every permission request with a refusal, as the system dialog would. */
    private val refusing = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                requests++
                dispatchResult(requestCode, false)
            }
        }
    }

    @Test
    fun theSecondRefusal_offersTheAppSettings() {
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides refusing) {
                TflTestSurface { QrScanner(onCode = {}) }
            }
        }
        val android = shadowOf(composeRule.activity.packageManager)

        // First refusal: Android would still ask again.
        android.setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        composeRule.onNodeWithText("Allow camera").performClick()
        composeRule.onNodeWithText("Camera needed to scan").assertExists()

        // Second refusal: Android won't ask again, so only the settings can turn it on.
        android.setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, false)
        composeRule.onNodeWithText("Allow camera").performClick()
        composeRule.onNodeWithText("Camera access is off").assertExists()
        composeRule.onNodeWithText("Open settings").assertExists()
        assertEquals(2, requests)
    }
}
