package app.tfl.core.transport.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.tfl.core.transport.PowerSaving
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Saving battery: the system's power saver is on, or the battery is under 15%. */
@Singleton
class AndroidPowerSaving @Inject constructor(@param:ApplicationContext private val context: Context) : PowerSaving {

    private val state = MutableStateFlow(false)
    private var batteryPercent = -1

    override val saving: StateFlow<Boolean> = state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update(intent)
    }

    init {
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        // The battery broadcast is sticky: registering returns the current level at once.
        update(ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED))
    }

    private fun update(intent: Intent?) {
        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (level >= 0) batteryPercent = level * 100 / scale
        }
        val powerSaver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        state.value = powerSaver || batteryPercent in 0 until LOW_BATTERY_PERCENT
    }

    private companion object {
        const val LOW_BATTERY_PERCENT = 15
    }
}
