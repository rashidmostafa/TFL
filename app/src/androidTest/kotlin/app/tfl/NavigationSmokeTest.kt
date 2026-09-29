package app.tfl

import android.view.WindowManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** On-device smoke test: `./gradlew connectedDebugAndroidTest` with a phone or emulator attached. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabsOpen_andWindowIsSecure() {
        val flags = composeRule.activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)

        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        composeRule.onNode(hasText("Vault") and isTab).performClick()
        composeRule.onNodeWithText("Lock vault now").assertExists()
        composeRule.onNode(hasText("Settings") and isTab).performClick()
        composeRule.onNodeWithText("Valkyrie-7").assertExists()
    }
}
