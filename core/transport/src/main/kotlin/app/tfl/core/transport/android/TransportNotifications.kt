package app.tfl.core.transport.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.session.di.ApplicationScope
import app.tfl.core.transport.MessageAlerts
import app.tfl.core.transport.R
import app.tfl.core.transport.service.TransportForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TFL's two notifications. Neither ever carries message content.
 *
 * - The running notification (silent, collapsed, hidden from the lock screen): "TFL is running",
 *   with a Stop action. While the calculator disguise is on, it's the calculator's name and icon.
 * - New-message alerts: "New message", or the friend's name when "Show sender name" is on. A
 *   secure lock screen shows "New message" at most: an alert with a name is secret, so it isn't
 *   shown there at all, whatever the phone's lock-screen setting. While disguised, neither the word
 *   "message" nor the name appears.
 *
 * Android still shows the app's installed name (TFL) in a notification's header; no app can change
 * that. Channels are named neutrally ("Background", "Alerts") as they're listed in system settings.
 */
@Singleton
class TransportNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val disguise: CalculatorDisguise,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_BACKGROUND, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(context.getString(R.string.transport_channel_background))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.transport_channel_alerts))
                    .build(),
            ),
        )
    }

    /** The foreground service's notification. */
    fun running(): Notification {
        val disguised = disguise.isEnabled()
        val stop = PendingIntent.getService(
            context,
            REQUEST_STOP,
            Intent(context, TransportForegroundService::class.java).setAction(TransportForegroundService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_BACKGROUND)
            .setSmallIcon(icon(disguised))
            .setContentTitle(context.getString(if (disguised) R.string.transport_disguised_title else R.string.transport_running))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .addAction(0, context.getString(R.string.transport_stop), stop)
            .build()
    }

    /** A new-message alert; [from] is the friend's name, or null to leave it out. */
    fun alert(from: String?): Notification {
        val disguised = disguise.isEnabled()
        val publicTitle = context.getString(if (disguised) R.string.transport_disguised_title else R.string.transport_new_message)
        val public = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(icon(disguised))
            .setContentTitle(publicTitle)
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(icon(disguised))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        if (from != null && !disguised) {
            // Secret, not private: a phone set to show all notification content on its lock screen
            // shows a private notification there in full, name included. A secret one isn't shown.
            builder.setContentTitle(from)
                .setContentText(context.getString(R.string.transport_new_message))
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
        } else {
            builder.setContentTitle(publicTitle)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
        }
        return builder.build()
    }

    /** Posts an alert, if notifications are allowed. */
    @SuppressLint("MissingPermission")
    fun postAlert(from: String?) {
        if (canPost()) manager.notify(ALERT_ID, alert(from))
    }

    fun clearAlerts() = manager.cancel(ALERT_ID)

    /** From now on, what's showing changes with the disguise ([restyle]). */
    fun followDisguise() {
        scope.launch { disguise.enabled.drop(1).collect { restyle() } }
    }

    /**
     * After the disguise is switched: the running notification, if it's showing, is drawn again in
     * the new look, and alerts already in the shade, which still have the old one, are cleared.
     */
    @SuppressLint("MissingPermission")
    internal fun restyle() {
        clearAlerts()
        val showing = context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == RUNNING_ID }
        if (showing && canPost()) manager.notify(RUNNING_ID, running())
    }

    /** The permission exists from Android 13; before, checking it would always read "denied" (lint doesn't see the version check). */
    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun icon(disguised: Boolean) = if (disguised) R.drawable.ic_stat_calculator else R.drawable.ic_stat_tfl

    /** Opens whichever launcher entry is on: TFL's lock screen, or the calculator while disguised. */
    private fun openApp(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, REQUEST_OPEN, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_BACKGROUND = "tfl.background"
        const val CHANNEL_ALERTS = "tfl.alerts"
        const val RUNNING_ID = 1
        const val ALERT_ID = 2
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN = 2
    }
}

/** New-message alerts through [TransportNotifications]. */
class AndroidMessageAlerts @Inject constructor(private val notifications: TransportNotifications) : MessageAlerts {
    override fun newMessage(from: String?) = notifications.postAlert(from)

    override fun clear() = notifications.clearAlerts()
}
