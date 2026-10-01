package app.tfl.core.transport.radio

import kotlinx.coroutines.flow.Flow

/**
 * Nearby Connections, reduced to what TFL uses: advertise under a name, discover, connect, and
 * send byte payloads. [GoogleNearbyRadio] is the real one; [InMemoryRadio] joins phones in tests
 * and the debug build's simulated friend. Nothing here is trusted: links authenticate themselves.
 */
interface Radio {
    /** The largest payload one [send] can carry. */
    val maxPayloadBytes: Int

    /** Everything the radio reports, in order. Collect it once. */
    val events: Flow<RadioEvent>

    suspend fun startAdvertising(name: String): Boolean

    suspend fun stopAdvertising()

    suspend fun startDiscovery(): Boolean

    suspend fun stopDiscovery()

    /** Asks [endpointId] to connect; the outcome arrives as events. */
    suspend fun requestConnection(name: String, endpointId: String): Boolean

    /** Both phones accept a connection before it opens. */
    suspend fun acceptConnection(endpointId: String): Boolean

    /** Turns down a connection being set up; both phones then see it fail. */
    suspend fun rejectConnection(endpointId: String)

    /** True once the whole payload went out. */
    suspend fun send(endpointId: String, bytes: ByteArray): Boolean

    suspend fun disconnect(endpointId: String)

    /** Stops advertising and discovery and drops every connection. */
    suspend fun stopAll()
}

sealed interface RadioEvent {
    val endpointId: String

    /** Discovery found a phone advertising [name]. */
    data class Found(override val endpointId: String, val name: String) : RadioEvent

    data class Lost(override val endpointId: String) : RadioEvent

    /**
     * A connection is being set up with a phone advertising [name]; [incoming] when the other phone
     * asked. Accept it to go on.
     */
    data class Initiated(override val endpointId: String, val incoming: Boolean, val name: String) : RadioEvent

    data class Connected(override val endpointId: String) : RadioEvent

    /** The attempt to connect failed or was refused. */
    data class ConnectFailed(override val endpointId: String) : RadioEvent

    data class Disconnected(override val endpointId: String) : RadioEvent

    class Received(override val endpointId: String, val bytes: ByteArray) : RadioEvent
}
