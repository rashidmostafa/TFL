package app.tfl.feature.onboarding.stealth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.session.CalculatorDisguise
import dagger.hilt.android.AndroidEntryPoint

/**
 * The launcher entry while the disguise is on. The only TFL window without FLAG_SECURE, so used as a
 * calculator it looks like any calculator in recent apps. After the secret code it leaves no trace.
 */
@AndroidEntryPoint
class CalculatorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            TflTheme {
                CalculatorRoute(onOpenTfl = ::openTfl)
            }
        }
    }

    /**
     * TFL opens in its own task (lock screen first). The calculator then removes its task from recent
     * apps rather than just closing: its last picture there would show the secret code just typed.
     */
    private fun openTfl() {
        startActivity(Intent().setClassName(this, CalculatorDisguise.MAIN_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finishAndRemoveTask()
    }
}
