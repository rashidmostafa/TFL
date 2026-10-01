package app.tfl.core.transport.debug

import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.transport.TransportLog
import app.tfl.core.transport.radio.FaultyRadio
import app.tfl.core.transport.radio.GoogleNearbyRadio
import app.tfl.core.transport.radio.Radio
import app.tfl.core.transport.radio.RadioFaults
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds: Nearby joined with the simulated friend's in-memory room, with fault injection in
 * the way, and a log the Developer screen shows.
 */
@Module
@InstallIn(SingletonComponent::class)
object TransportVariantModule {
    @Provides
    @Singleton
    fun radio(google: GoogleNearbyRadio, air: SimulatedAir, faults: RadioFaults): Radio = FaultyRadio(CombinedRadio(google, air.phone), faults)

    @Provides
    fun log(events: TransportEvents): TransportLog = events
}

/** The transport's last events, in memory only: never content, names or keys (see [TransportLog]). */
@Singleton
class TransportEvents @Inject constructor(private val clock: DeviceClock) : TransportLog {
    class Event(val atMillis: Long, val text: String)

    private val recent = MutableStateFlow<List<Event>>(emptyList())

    val events: StateFlow<List<Event>> = recent.asStateFlow()

    @Synchronized
    override fun event(message: () -> String) {
        recent.value = (recent.value + Event(clock.currentTimeMillis(), message())).takeLast(MAX_EVENTS)
    }

    @Synchronized
    fun clear() {
        recent.value = emptyList()
    }

    private companion object {
        const val MAX_EVENTS = 500
    }
}
