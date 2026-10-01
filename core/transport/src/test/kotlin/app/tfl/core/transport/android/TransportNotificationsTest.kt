package app.tfl.core.transport.android

import android.Manifest
import android.app.Application
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.testing.session.SessionFixture
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Notifications never carry message content, and the lock screen never gets a name. */
@RunWith(AndroidJUnit4::class)
class TransportNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = SessionFixture(context)
    private val notifications = TransportNotifications(context, fixture.disguise, TestScope())

    private fun Notification.title() = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

    /** The calculator alias exists only in the app's manifest; here it's added for the disguise to switch on. */
    private fun disguise() {
        val alias = ComponentName(context.packageName, CalculatorDisguise.CALCULATOR_ALIAS)
        shadowOf(context.packageManager).addOrUpdateActivity(ActivityInfo().apply {
            packageName = alias.packageName
            name = alias.className
        })
        context.packageManager.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        assertTrue(fixture.disguise.isEnabled())
    }

    @Test
    fun `the running notification is silent, hidden from the lock screen, and can stop`() {
        val running = notifications.running()
        assertEquals("TFL is running", running.title())
        assertEquals(TransportNotifications.CHANNEL_BACKGROUND, running.channelId)
        assertEquals(Notification.VISIBILITY_SECRET, running.visibility)
        assertTrue(running.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf("Stop"), running.actions.map { it.title.toString() })
    }

    @Test
    fun `an alert says only "New message", and the lock screen gets no more`() {
        val alert = notifications.alert(from = null)
        assertEquals("New message", alert.title())
        assertNull(alert.text())
        assertEquals(Notification.VISIBILITY_PRIVATE, alert.visibility)
        assertEquals("New message", alert.publicVersion.title())
        assertNull(alert.publicVersion.text())
    }

    @Test
    fun `with the sender shown, the alert stays off the lock screen, whatever the phone's setting`() {
        val alert = notifications.alert(from = "Elena")
        assertEquals("Elena", alert.title())
        assertEquals("New message", alert.text())
        // Private would show the name on phones set to show all content on the lock screen.
        assertEquals(Notification.VISIBILITY_SECRET, alert.visibility)
        assertNull(alert.publicVersion)
    }

    @Test
    fun `while disguised, notifications are the calculator's`() {
        disguise()
        assertEquals("Calculator", notifications.running().title())
        val alert = notifications.alert(from = "Elena")
        assertEquals("Calculator", alert.title())
        assertNull("no name, no \"message\"", alert.text())
        assertEquals("Calculator", alert.publicVersion.title())
    }

    @Test
    fun `channels have neutral names`() {
        notifications.createChannels()
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        assertEquals("Background", manager.getNotificationChannel(TransportNotifications.CHANNEL_BACKGROUND).name)
        assertEquals("Alerts", manager.getNotificationChannel(TransportNotifications.CHANNEL_ALERTS).name)
    }

    @Test
    fun `switching the disguise redraws the running notification and clears old alerts`() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications.createChannels()
        // As the service shows it, with an alert from before.
        manager.notify(TransportNotifications.RUNNING_ID, notifications.running())
        notifications.postAlert(from = "Elena")
        disguise()

        notifications.restyle()

        val showing = manager.activeNotifications.associate { it.id to it.notification.title() }
        assertEquals(mapOf(TransportNotifications.RUNNING_ID to "Calculator"), showing)
    }

}
