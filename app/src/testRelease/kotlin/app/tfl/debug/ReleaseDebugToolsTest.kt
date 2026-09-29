package app.tfl.debug

import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Release builds must carry no developer tools and no way to allow screenshots. */
@RunWith(AndroidJUnit4::class)
class ReleaseDebugToolsTest {

    @Test
    fun screenshotsCanNeverBeAllowed() {
        assertFalse(DebugTools.allowScreenshots.value)
    }

    @Test
    fun settingsHasNoDeveloperSection() {
        assertNull(DebugTools.settingsSection(NavController(ApplicationProvider.getApplicationContext())))
    }
}
