package app.tfl.core.transport.service

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.tfl.core.common.log.TflLog
import app.tfl.core.transport.TransportController
import app.tfl.core.transport.TransportService
import app.tfl.core.transport.android.TransportNotifications
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Keeps TFL's process alive while the transport runs (type connectedDevice), with the discreet
 * running notification. Its Stop action stops Nearby until the next unlock; while TFL is locked
 * that also wipes the transport's keys. Not restarted by the system if killed: its keys were in
 * memory and are gone.
 */
@AndroidEntryPoint
class TransportForegroundService : Service() {

    @Inject
    lateinit var controller: TransportController

    @Inject
    lateinit var notifications: TransportNotifications

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stopFromNotification()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, TransportNotifications.RUNNING_ID, notifications.running(), type)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "app.tfl.transport.STOP"
    }
}

/** Starts and stops [TransportForegroundService]. */
class AndroidTransportService @Inject constructor(@param:ApplicationContext private val context: Context) : TransportService {
    override fun setRunning(running: Boolean) {
        val intent = Intent(context, TransportForegroundService::class.java)
        if (!running) {
            context.stopService(intent)
            return
        }
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses to start it from the background; it's started at the next unlock.
            val refused = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException
            TflLog.w(TAG, e) { if (refused) "Not allowed to start from the background" else "Couldn't start" }
        }
    }

    private companion object {
        const val TAG = "TransportService"
    }
}
