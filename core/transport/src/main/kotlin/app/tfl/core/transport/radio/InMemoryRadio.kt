package app.tfl.core.transport.radio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Phones in one room, with no Bluetooth involved: [InMemoryRadio]s that find, connect to and send
 * to each other the way Nearby Connections does. Tests use it for two or more phones in one
 * process, and the debug build's simulated friend lives on it. [setInRange] moves phones apart.
 */
class Airwaves(val maxPayloadBytes: Int = NEARBY_MAX_BYTES) {

    private val radios = mutableListOf<InMemoryRadio>()
    private val apart = mutableSetOf<Set<String>>()
    private val pending = mutableMapOf<Set<String>, MutableSet<String>>()
    private val connections = mutableSetOf<Set<String>>()

    /** A phone's radio; others know it by [address], as Nearby's endpoint id. */
    @Synchronized
    fun radio(address: String): InMemoryRadio {
        require(radios.none { it.address == address }) { "Address in use" }
        return InMemoryRadio(this, address).also { radios += it }
    }

    @Synchronized
    fun isConnected(a: InMemoryRadio, b: InMemoryRadio): Boolean = setOf(a.address, b.address) in connections

    /** Out of range, connections between the two drop and neither can find the other. */
    @Synchronized
    fun setInRange(a: InMemoryRadio, b: InMemoryRadio, inRange: Boolean) {
        val pair = setOf(a.address, b.address)
        if (inRange) {
            if (!apart.remove(pair)) return
            announce(a, b)
            announce(b, a)
        } else {
            if (!apart.add(pair)) return
            if (connections.remove(pair)) {
                a.emit(RadioEvent.Disconnected(b.address))
                b.emit(RadioEvent.Disconnected(a.address))
            }
            if (pending.remove(pair) != null) {
                a.emit(RadioEvent.ConnectFailed(b.address))
                b.emit(RadioEvent.ConnectFailed(a.address))
            }
            if (a.discovering && b.advertisedName != null) a.emit(RadioEvent.Lost(b.address))
            if (b.discovering && a.advertisedName != null) b.emit(RadioEvent.Lost(a.address))
        }
    }

    private fun inRange(a: InMemoryRadio, b: InMemoryRadio) = a !== b && setOf(a.address, b.address) !in apart

    /** [finder] discovers [advertiser], if it's looking and they're in range. */
    private fun announce(finder: InMemoryRadio, advertiser: InMemoryRadio) {
        val name = advertiser.advertisedName ?: return
        if (finder.discovering && inRange(finder, advertiser)) finder.emit(RadioEvent.Found(advertiser.address, name))
    }

    private fun find(address: String) = radios.firstOrNull { it.address == address }

    @Synchronized
    internal fun startAdvertising(radio: InMemoryRadio, name: String): Boolean {
        if (radio.advertisedName != null) return false
        radio.advertisedName = name
        radios.forEach { announce(it, radio) }
        return true
    }

    @Synchronized
    internal fun stopAdvertising(radio: InMemoryRadio) {
        radio.advertisedName ?: return
        radio.advertisedName = null
        radios.filter { it.discovering && inRange(it, radio) }.forEach { it.emit(RadioEvent.Lost(radio.address)) }
    }

    @Synchronized
    internal fun startDiscovery(radio: InMemoryRadio): Boolean {
        if (radio.discovering) return false
        radio.discovering = true
        radios.forEach { announce(radio, it) }
        return true
    }

    @Synchronized
    internal fun stopDiscovery(radio: InMemoryRadio) {
        radio.discovering = false
    }

    @Synchronized
    internal fun requestConnection(from: InMemoryRadio, name: String, to: String): Boolean {
        val other = find(to) ?: return false
        val otherName = other.advertisedName
        val pair = setOf(from.address, to)
        if (otherName == null || !inRange(from, other) || pair in connections || pair in pending) return false
        pending[pair] = mutableSetOf()
        from.emit(RadioEvent.Initiated(to, incoming = false, name = otherName))
        other.emit(RadioEvent.Initiated(from.address, incoming = true, name = name))
        return true
    }

    @Synchronized
    internal fun accept(radio: InMemoryRadio, endpointId: String): Boolean {
        val pair = setOf(radio.address, endpointId)
        val accepted = pending[pair] ?: return false
        accepted += radio.address
        if (accepted.size == 2) {
            pending.remove(pair)
            connections += pair
            radio.emit(RadioEvent.Connected(endpointId))
            find(endpointId)?.emit(RadioEvent.Connected(radio.address))
        }
        return true
    }

    @Synchronized
    internal fun reject(radio: InMemoryRadio, endpointId: String) {
        if (pending.remove(setOf(radio.address, endpointId)) == null) return
        radio.emit(RadioEvent.ConnectFailed(endpointId))
        find(endpointId)?.emit(RadioEvent.ConnectFailed(radio.address))
    }

    @Synchronized
    internal fun send(from: InMemoryRadio, to: String, bytes: ByteArray): Boolean {
        if (bytes.size > maxPayloadBytes || setOf(from.address, to) !in connections) return false
        find(to)?.emit(RadioEvent.Received(from.address, bytes.copyOf()))
        return true
    }

    /** As in Nearby, only the other phone hears about a disconnection it didn't start. */
    @Synchronized
    internal fun disconnect(from: InMemoryRadio, endpointId: String) {
        val pair = setOf(from.address, endpointId)
        if (connections.remove(pair)) find(endpointId)?.emit(RadioEvent.Disconnected(from.address))
        if (pending.remove(pair) != null) find(endpointId)?.emit(RadioEvent.ConnectFailed(from.address))
    }

    @Synchronized
    internal fun stopAll(radio: InMemoryRadio) {
        stopAdvertising(radio)
        stopDiscovery(radio)
        (connections + pending.keys).filter { radio.address in it }.forEach { pair ->
            disconnect(radio, pair.first { it != radio.address })
        }
    }

    companion object {
        /** Nearby Connections' limit for one byte payload (ConnectionsClient.MAX_BYTES_DATA_SIZE in 19.5.1). */
        const val NEARBY_MAX_BYTES = 1_047_552
    }
}

class InMemoryRadio internal constructor(private val airwaves: Airwaves, val address: String) : Radio {

    private val channel = Channel<RadioEvent>(Channel.UNLIMITED)

    @Volatile
    internal var advertisedName: String? = null

    @Volatile
    internal var discovering = false

    override val maxPayloadBytes: Int get() = airwaves.maxPayloadBytes

    override val events: Flow<RadioEvent> = channel.receiveAsFlow()

    internal fun emit(event: RadioEvent) {
        channel.trySend(event)
    }

    override suspend fun startAdvertising(name: String) = airwaves.startAdvertising(this, name)

    override suspend fun stopAdvertising() = airwaves.stopAdvertising(this)

    override suspend fun startDiscovery() = airwaves.startDiscovery(this)

    override suspend fun stopDiscovery() = airwaves.stopDiscovery(this)

    override suspend fun requestConnection(name: String, endpointId: String) = airwaves.requestConnection(this, name, endpointId)

    override suspend fun acceptConnection(endpointId: String) = airwaves.accept(this, endpointId)

    override suspend fun rejectConnection(endpointId: String) = airwaves.reject(this, endpointId)

    override suspend fun send(endpointId: String, bytes: ByteArray) = airwaves.send(this, endpointId, bytes)

    override suspend fun disconnect(endpointId: String) = airwaves.disconnect(this, endpointId)

    override suspend fun stopAll() = airwaves.stopAll(this)
}
