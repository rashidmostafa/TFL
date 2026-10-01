package app.tfl.core.transport

import app.tfl.core.crypto.lock.DeviceClock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import app.tfl.core.model.Transport as TransportKind

/** Wall and elapsed time that follow the test's virtual time, starting on 21 September 2026. */
class SchedulerClock(private val scheduler: TestCoroutineScheduler, private val start: Long = 1_790_000_000_000) : DeviceClock {
    override fun currentTimeMillis(): Long = start + scheduler.currentTime
    override fun elapsedRealtime(): Long = 10_000_000 + scheduler.currentTime
    override fun bootCount(): Int = 1
}

/** A transport the test drives: who's reachable, and whether sends succeed. */
class FakeTransport(override val kind: TransportKind = TransportKind.NEARBY) : Transport {
    val reach = MutableStateFlow<Set<Long>>(emptySet())
    override val reachable: StateFlow<Set<Long>> = reach

    private val incoming = Channel<ReceivedEnvelope>(Channel.UNLIMITED)
    override val received: Flow<ReceivedEnvelope> = incoming.receiveAsFlow()

    /** What went out: (contact, envelope), in order. */
    val sent = mutableListOf<Pair<Long, ByteArray>>()
    var failing = false

    override suspend fun send(contactId: Long, envelope: ByteArray): Boolean {
        if (failing || contactId !in reach.value) return false
        sent += contactId to envelope
        return true
    }
}

class FakeReadiness(ready: Boolean = true) : NearbyReadiness {
    override val permitted = MutableStateFlow(ready)
    override val ready = MutableStateFlow(ready)
}

class RecordingWakeup : ScheduledWakeup {
    var at: Long? = null
        private set

    override fun set(atMillis: Long?) {
        at = atMillis
    }
}

class FakePowerSaving : PowerSaving {
    override val saving = MutableStateFlow(false)
}

class FakeService : TransportService {
    var running = false
        private set

    override fun setRunning(running: Boolean) {
        this.running = running
    }
}

class RecordingAlerts : MessageAlerts {
    val alerts = mutableListOf<String?>()

    override fun newMessage(from: String?) {
        alerts += from
    }
}
