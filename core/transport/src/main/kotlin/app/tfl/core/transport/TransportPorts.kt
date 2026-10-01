package app.tfl.core.transport

import app.tfl.core.model.message.OutboxItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Tells the user about new messages. Never with content: see the Android implementation. */
fun interface MessageAlerts {
    /** A message arrived from [from], or from someone unnamed when the sender is hidden. */
    fun newMessage(from: String?)

    /** The user is looking at their chats: alerts go. */
    fun clear() {}
}

/** Whether Nearby can run on this phone. */
interface NearbyReadiness {
    /** Its permissions are granted: enough for the foreground service. */
    val permitted: StateFlow<Boolean>

    /** Also Bluetooth on, and Location on where Android needs it: enough for the radio. */
    val ready: StateFlow<Boolean>
}

/** Wakes the transport when the next scheduled message is due; null cancels. */
fun interface ScheduledWakeup {
    fun set(atMillis: Long?)
}

/** Whether to save battery: the system's power saver, or a low battery. */
interface PowerSaving {
    val saving: StateFlow<Boolean>
}

/** Starts and stops the foreground service that keeps the transport running. */
fun interface TransportService {
    fun setRunning(running: Boolean)
}

/** What screens show about the transport. */
interface TransportStatus {
    /** Friends linked right now, by contact id. */
    val reachable: StateFlow<Set<Long>>

    /** Whether Nearby is running. */
    val running: StateFlow<Boolean>
}

/** Which friend's conversation is on screen: their messages don't raise alerts. */
@Singleton
class ChatPresence @Inject constructor() {
    val visibleContact = MutableStateFlow<Long?>(null)
}

/**
 * The outbox as [Messenger] just left it, handed straight to the transport: TFL may lock before
 * the database reports the change, and what was queued should still go out while locked.
 */
@Singleton
class OutboxFeed @Inject constructor() {
    /** [owner] is the identity key of the profile whose outbox it is. */
    class Snapshot(val owner: ByteArray, val items: List<OutboxItem>)

    private val flow = MutableSharedFlow<Snapshot>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val snapshots: SharedFlow<Snapshot> = flow.asSharedFlow()

    fun publish(snapshot: Snapshot) {
        flow.tryEmit(snapshot)
    }
}
