package app.tfl

import android.graphics.Color
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.debug.DebugTools
import app.tfl.ui.TflApp
import app.tfl.ui.rememberTflAppState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Before the first frame: nothing in TFL may be screenshotted, recorded, cast, or shown in recents.
        window.setScreenshotsAllowed(false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            // Always false in release builds; debug builds can relax it from Settings for testing.
            val allowScreenshots by DebugTools.allowScreenshots
            LaunchedEffect(allowScreenshots) { window.setScreenshotsAllowed(allowScreenshots) }

            TflTheme {
                TflApp(appState = rememberTflAppState(), appVersion = BuildConfig.VERSION_NAME)
            }
        }
    }
}

private fun Window.setScreenshotsAllowed(allowed: Boolean) {
    if (allowed) {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
