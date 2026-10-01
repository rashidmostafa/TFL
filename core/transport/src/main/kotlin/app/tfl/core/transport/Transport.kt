package app.tfl.core.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import app.tfl.core.model.Transport as TransportKind

/**
 * A way envelopes reach friends: Nearby now; Tor (Phase 4) and relays (Phase 6) later. It carries
 * opaque bytes only: who a friend is was proven when their link came up, and what an envelope
 * says only its recipient can read.
 */
interface Transport {
    val kind: TransportKind

    /** Friends reachable through this transport right now, by contact id. */
    val reachable: StateFlow<Set<Long>>

    /** Envelopes as they arrive, with the friend whose link carried them. */
    val received: Flow<ReceivedEnvelope>

    /** Hands [envelope] to [contactId]'s link; true once it has gone out. */
    suspend fun send(contactId: Long, envelope: ByteArray): Boolean
}

class ReceivedEnvelope(val contactId: Long, val bytes: ByteArray, val via: TransportKind, val receivedAtMillis: Long)
