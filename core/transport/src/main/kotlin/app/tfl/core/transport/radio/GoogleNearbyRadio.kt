package app.tfl.core.transport.radio

import android.content.Context
import app.tfl.core.common.log.TflLog
import app.tfl.core.transport.nearby.NearbyTransport
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionOptions
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The real radio: Google's Nearby Connections in Play services, strategy P2P_CLUSTER, service id
 * [NearbyTransport.SERVICE_ID]. It only moves bytes. TFL uses none of Nearby's authentication
 * tokens or endpoint info, and trusts nothing it reports. Wi-Fi "upgrades" that would take the
 * phone off its own Wi-Fi network are turned off: TFL's frames are small.
 */
@Singleton
class GoogleNearbyRadio @Inject constructor(@param:ApplicationContext private val context: Context) : Radio {

    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val channel = Channel<RadioEvent>(Channel.UNLIMITED)

    /** Sends waiting for Nearby to report how they went, by payload id. */
    private val transfers = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()

    private val trouble = MutableStateFlow<Int?>(null)

    /** The last status Nearby refused to start with (a missing permission or setting), or null. */
    val lastRefusal: StateFlow<Int?> = trouble.asStateFlow()

    override val maxPayloadBytes: Int = ConnectionsClient.MAX_BYTES_DATA_SIZE

    override val events: Flow<RadioEvent> = channel.receiveAsFlow()

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            emit(RadioEvent.Initiated(endpointId, info.isIncomingConnection, info.endpointName))
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            emit(if (result.status.isSuccess) RadioEvent.Connected(endpointId) else RadioEvent.ConnectFailed(endpointId))
        }

        override fun onDisconnected(endpointId: String) = emit(RadioEvent.Disconnected(endpointId))
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == NearbyTransport.SERVICE_ID) emit(RadioEvent.Found(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) = emit(RadioEvent.Lost(endpointId))
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.takeIf { it.type == Payload.Type.BYTES }?.asBytes()
            if (bytes != null) emit(RadioEvent.Received(endpointId, bytes)) else client.cancelPayload(payload.id)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> transfers.remove(update.payloadId)?.complete(true)
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> transfers.remove(update.payloadId)?.complete(false)
            }
        }
    }

    private fun emit(event: RadioEvent) {
        channel.trySend(event)
    }

    override suspend fun startAdvertising(name: String): Boolean {
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).setDisruptiveUpgrade(false).build()
        return client.startAdvertising(name, NearbyTransport.SERVICE_ID, lifecycle, options).succeeded()
    }

    override suspend fun stopAdvertising() = client.stopAdvertising()

    override suspend fun startDiscovery(): Boolean {
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        return client.startDiscovery(NearbyTransport.SERVICE_ID, discovery, options).succeeded()
    }

    override suspend fun stopDiscovery() = client.stopDiscovery()

    override suspend fun requestConnection(name: String, endpointId: String): Boolean {
        val options = ConnectionOptions.Builder().setDisruptiveUpgrade(false).build()
        return client.requestConnection(name, endpointId, lifecycle, options).succeeded()
    }

    override suspend fun acceptConnection(endpointId: String): Boolean = client.acceptConnection(endpointId, payloads).succeeded()

    override suspend fun rejectConnection(endpointId: String) {
        client.rejectConnection(endpointId).succeeded()
    }

    override suspend fun send(endpointId: String, bytes: ByteArray): Boolean {
        val payload = Payload.fromBytes(bytes)
        val done = CompletableDeferred<Boolean>()
        transfers[payload.id] = done
        if (!client.sendPayload(endpointId, payload).succeeded()) {
            transfers.remove(payload.id)
            return false
        }
        return withTimeoutOrNull(SEND_TIMEOUT_MILLIS) { done.await() } ?: false.also { transfers.remove(payload.id) }
    }

    override suspend fun disconnect(endpointId: String) = client.disconnectFromEndpoint(endpointId)

    override suspend fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        transfers.values.forEach { it.complete(false) }
        transfers.clear()
    }

    /** Waits for a Play services task; a refusal to start is kept in [lastRefusal] for the setup screen. */
    private suspend fun Task<*>.succeeded(): Boolean = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val status = (task.exception as? ApiException)?.statusCode
            if (status != null && status in SETUP_PROBLEMS) trouble.value = status
            if (task.isSuccessful) trouble.value = null
            if (status != null && status !in QUIET) TflLog.w(TAG) { "Nearby refused: ${ConnectionsStatusCodes.getStatusCodeString(status)}" }
            continuation.resume(task.isSuccessful)
        }
    }

    private companion object {
        const val TAG = "GoogleNearbyRadio"

        /** A payload that hasn't gone out in this long isn't going to. */
        const val SEND_TIMEOUT_MILLIS = 60_000L

        /** Nearby's refusals the user can fix: a setting or a permission. */
        val SETUP_PROBLEMS = setOf(
            ConnectionsStatusCodes.MISSING_SETTING_LOCATION_MUST_BE_ON,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_COARSE_LOCATION,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH,
            ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADMIN,
            ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES,
        )

        /** Expected along the way: asking twice, or a phone already gone. */
        val QUIET = setOf(
            ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING,
            ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING,
            ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT,
            ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN,
        )
    }
}
