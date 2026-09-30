package app.tfl.core.session

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.testing.session.SessionFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AutoLockTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val fixture = SessionFixture(application)
    private val timers = TestScope()
    private val autoLock = AutoLock(fixture.session, fixture.disguise, timers)
    private val activity = Robolectric.buildActivity(Activity::class.java)

    @After
    fun tearDown() {
        application.unregisterActivityLifecycleCallbacks(autoLock)
        fixture.database.close()
    }

    private suspend fun unlockedWith(timeout: AutoLockTimeout) {
        fixture.session.start()
        fixture.session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))
        fixture.session.setAutoLock(timeout)
        autoLock.install(application)
        activity.setup()
    }

    @Test
    fun `immediately locks as soon as TFL leaves the screen`() = runTest {
        unlockedWith(AutoLockTimeout.IMMEDIATELY)
        activity.stop()
        timers.runCurrent()
        assertEquals(GateState.Locked, fixture.session.state.value)
    }

    @Test
    fun `a timeout locks only after that long in the background`() = runTest {
        unlockedWith(AutoLockTimeout.SECONDS_30)
        activity.stop()
        timers.advanceTimeBy(29_000)
        assertEquals(true, fixture.session.isUnlocked)
        timers.advanceTimeBy(2_000)
        assertEquals(GateState.Locked, fixture.session.state.value)
    }

    @Test
    fun `with the calculator disguise on, leaving locks at once whatever the timeout`() = runTest {
        unlockedWith(AutoLockTimeout.MINUTES_5)
        fixture.lockSettings.enableDisguise("314159".encodeToByteArray())
        activity.stop()
        timers.runCurrent()
        assertEquals(GateState.Locked, fixture.session.state.value)
    }

    @Test
    fun `coming back in time cancels the lock`() = runTest {
        unlockedWith(AutoLockTimeout.SECONDS_30)
        activity.stop()
        timers.advanceTimeBy(20_000)
        activity.start()
        timers.advanceTimeBy(60_000)
        assertEquals(true, fixture.session.isUnlocked)
    }
}
