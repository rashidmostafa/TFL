package app.tfl.core.transport.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import app.tfl.core.session.di.ApplicationScope
import app.tfl.core.transport.NearbyReadiness
import app.tfl.core.transport.radio.GoogleNearbyRadio
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The runtime permissions Nearby Connections needs on each Android version (Google's list). Nearby
 * Wi-Fi devices exists from Android 13; on 12L the Bluetooth permissions are all Nearby needs.
 * Constants from later versions are only used on those versions.
 */
@SuppressLint("InlinedApi")
object NearbyPermissions {
    /** What Nearby won't start without. */
    fun required(sdk: Int): List<String> = buildList {
        if (sdk >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        when {
            sdk <= Build.VERSION_CODES.P -> add(Manifest.permission.ACCESS_COARSE_LOCATION)
            sdk <= Build.VERSION_CODES.S -> add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (sdk >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
        if (sdk >= Build.VERSION_CODES.CINNAMON_BUN) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    /**
     * What to ask for in one go: Android 12 wants approximate location asked for with precise, and
     * from Android 13 notifications are asked for too (TFL's running notification and alerts).
     */
    fun toRequest(sdk: Int): List<String> = required(sdk) + buildList {
        if (sdk == Build.VERSION_CODES.S) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (sdk >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Android 11 and older only scan with Location switched on. */
    fun locationSwitchNeeded(sdk: Int): Boolean = sdk <= Build.VERSION_CODES.R
}

/** What Nearby still needs on this phone. */
data class NearbyStatus(
    /** Required permissions not granted. */
    val missing: List<String>,
    val bluetoothOn: Boolean,
    /** Whether Location must be switched on: Android 11 and older, or when Nearby asked for it. */
    val locationNeeded: Boolean,
    val locationOn: Boolean,
) {
    val permitted: Boolean get() = missing.isEmpty()
    val ready: Boolean get() = permitted && bluetoothOn && (!locationNeeded || locationOn)
}

/** What Nearby still needs, for the setup screen. */
interface NearbySetup {
    val status: StateFlow<NearbyStatus>

    /** Reads it all again: after a permission request, or back from system settings. */
    fun refresh()
}

/**
 * Nearby's readiness on this phone, re-read when Bluetooth or Location is switched, when Nearby
 * refuses to start, and when the app asks ([refresh], after a permission request or on resume).
 */
@Singleton
class AndroidNearbyReadiness @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val radio: GoogleNearbyRadio,
    @param:ApplicationScope scope: CoroutineScope,
) : NearbyReadiness, NearbySetup {

    private val current = MutableStateFlow(read())
    private val permittedState = MutableStateFlow(current.value.permitted)
    private val readyState = MutableStateFlow(current.value.ready)

    override val status: StateFlow<NearbyStatus> = current.asStateFlow()
    override val permitted: StateFlow<Boolean> = permittedState.asStateFlow()
    override val ready: StateFlow<Boolean> = readyState.asStateFlow()

    private val switches = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, switches, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { radio.lastRefusal.collect { refresh() } }
    }

    override fun refresh() {
        val status = read()
        current.value = status
        permittedState.value = status.permitted
        readyState.value = status.ready
    }

    private fun read(): NearbyStatus {
        val sdk = Build.VERSION.SDK_INT
        val missing = NearbyPermissions.required(sdk).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        val location = context.getSystemService(LocationManager::class.java)
        return NearbyStatus(
            missing = missing,
            bluetoothOn = bluetoothOn(),
            locationNeeded = NearbyPermissions.locationSwitchNeeded(sdk) ||
                radio.lastRefusal.value == ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON,
            locationOn = location != null && LocationManagerCompat.isLocationEnabled(location),
        )
    }

    /** Reading the switch needs no permission from Android 12; before, the install-time BLUETOOTH one. */
    private fun bluetoothOn(): Boolean = try {
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }
}
