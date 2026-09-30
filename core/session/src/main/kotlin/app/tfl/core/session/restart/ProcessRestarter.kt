package app.tfl.core.session.restart

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Kills the app process and starts it fresh, so no state from before a wipe survives in memory. */
fun interface ProcessRestarter {
    fun restart()
}

class AndroidProcessRestarter @Inject constructor(@ApplicationContext private val context: Context) : ProcessRestarter {
    override fun restart() {
        context.startActivity(
            Intent(context, RestartActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(RestartActivity.EXTRA_MAIN_PID, Process.myPid()),
        )
        Runtime.getRuntime().exit(0)
    }
}

/**
 * Runs in its own `:restart` process (see the manifest): makes sure the old main process is dead,
 * relaunches TFL, then exits.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0) Process.killProcess(mainPid)
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finish()
        Runtime.getRuntime().exit(0)
    }

    internal companion object {
        const val EXTRA_MAIN_PID = "main_pid"
    }
}
