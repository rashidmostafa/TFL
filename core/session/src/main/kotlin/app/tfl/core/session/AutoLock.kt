package app.tfl.core.session

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.tfl.core.session.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Locks TFL once none of its activities has been visible for the auto-lock timeout. Activities
 * stopping only to be recreated (rotation) don't count as leaving. While the calculator disguise is
 * on, TFL locks as soon as it leaves the screen, whatever the timeout: it has no card in recent apps
 * to come back through, only the calculator.
 */
@Singleton
class AutoLock @Inject constructor(
    private val session: AppSession,
    private val disguise: CalculatorDisguise,
    @param:ApplicationScope private val scope: CoroutineScope,
) : Application.ActivityLifecycleCallbacks {

    private var startedActivities = 0
    private var pendingLock: Job? = null

    fun install(application: Application) = application.registerActivityLifecycleCallbacks(this)

    override fun onActivityStarted(activity: Activity) {
        startedActivities++
        pendingLock?.cancel()
        pendingLock = null
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        if (startedActivities > 0 || activity.isChangingConfigurations || !session.isUnlocked) return
        val delayMillis = if (disguise.isEnabled()) 0 else session.autoLock.millis
        pendingLock = scope.launch {
            if (delayMillis > 0) delay(delayMillis)
            session.lock()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
