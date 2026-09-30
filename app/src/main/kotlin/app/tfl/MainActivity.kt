package app.tfl

import android.app.ActivityManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.session.AppSession
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.session.GateState
import app.tfl.debug.DebugTools
import app.tfl.ui.TflGate
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single TFL window. What it shows follows [AppSession.state]: onboarding, the lock screen, or
 * the app. A FragmentActivity because BiometricPrompt needs one.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var session: AppSession

    @Inject
    lateinit var disguise: CalculatorDisguise

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Before the first frame: nothing in TFL may be screenshotted, recorded, cast, or shown in recents.
        window.setScreenshotsAllowed(false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        splash.setKeepOnScreenCondition { session.state.value == GateState.Starting }
        lifecycleScope.launch { session.start() }
        setContent {
            // Always false in release builds; debug builds can relax it from Settings for testing.
            val allowScreenshots by DebugTools.allowScreenshots
            LaunchedEffect(allowScreenshots) { window.setScreenshotsAllowed(allowScreenshots) }

            val gate by session.state.collectAsStateWithLifecycle()
            TflTheme {
                TflGate(gate = gate, appVersion = BuildConfig.VERSION_NAME)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // With the disguise on, TFL's own task stays out of recent apps: the calculator is the way in.
        setExcludedFromRecents(disguise.isEnabled())
    }

    private fun setExcludedFromRecents(excluded: Boolean) {
        getSystemService(ActivityManager::class.java)?.appTasks
            ?.firstOrNull { it.taskInfo?.idOfTask() == taskId }
            ?.setExcludeFromRecents(excluded)
    }
}

private fun ActivityManager.RecentTaskInfo.idOfTask(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) taskId else @Suppress("DEPRECATION") id

private fun Window.setScreenshotsAllowed(allowed: Boolean) {
    if (allowed) {
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
