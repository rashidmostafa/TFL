package app.tfl.core.transport.debug

import app.tfl.core.transport.radio.Airwaves
import app.tfl.core.transport.radio.InMemoryRadio
import app.tfl.core.transport.radio.Radio
import app.tfl.core.transport.radio.RadioEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

/** Debug builds: the in-memory room this phone shares with the simulated friend. */
@Singleton
class SimulatedAir @Inject constructor() {
    private val airwaves = Airwaves()

    /** This phone's side, joined to the real radio by [CombinedRadio]. */
    val phone: InMemoryRadio = airwaves.radio(PREFIX + "phone")

    /** The simulated friend's radio. */
    val friend: InMemoryRadio = airwaves.radio(PREFIX + "friend")

    companion object {
        /** Endpoint ids on the simulated side start with this; Nearby's never do. */
        const val PREFIX = "sim-"
    }
}

/**
 * Debug builds: the real radio and the simulated one as one. Both advertise and discover; each
 * endpoint's calls go to the side it came from.
 */
class CombinedRadio(private val real: Radio, private val simulated: Radio) : Radio {

    override val maxPayloadBytes: Int get() = minOf(real.maxPayloadBytes, simulated.maxPayloadBytes)

    override val events: Flow<RadioEvent> = merge(real.events, simulated.events)

    private fun sideOf(endpointId: String) = if (endpointId.startsWith(SimulatedAir.PREFIX)) simulated else real

    override suspend fun startAdvertising(name: String): Boolean {
        val real = real.startAdvertising(name)
        val simulated = simulated.startAdvertising(name)
        return real || simulated
    }

    override suspend fun stopAdvertising() {
        real.stopAdvertising()
        simulated.stopAdvertising()
    }

    override suspend fun startDiscovery(): Boolean {
        val real = real.startDiscovery()
        val simulated = simulated.startDiscovery()
        return real || simulated
    }

    override suspend fun stopDiscovery() {
        real.stopDiscovery()
        simulated.stopDiscovery()
    }

    override suspend fun requestConnection(name: String, endpointId: String) = sideOf(endpointId).requestConnection(name, endpointId)

    override suspend fun acceptConnection(endpointId: String) = sideOf(endpointId).acceptConnection(endpointId)

    override suspend fun rejectConnection(endpointId: String) = sideOf(endpointId).rejectConnection(endpointId)

    override suspend fun send(endpointId: String, bytes: ByteArray) = sideOf(endpointId).send(endpointId, bytes)

    override suspend fun disconnect(endpointId: String) = sideOf(endpointId).disconnect(endpointId)

    override suspend fun stopAll() {
        real.stopAll()
        simulated.stopAll()
    }
}
