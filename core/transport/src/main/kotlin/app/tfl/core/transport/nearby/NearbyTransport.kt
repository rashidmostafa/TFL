package app.tfl.core.transport.nearby

import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.link.LinkChannel
import app.tfl.core.crypto.link.LinkHandshake
import app.tfl.core.crypto.link.LinkHandshakes
import app.tfl.core.crypto.link.LinkPeer
import app.tfl.core.crypto.link.LinkProgress
import app.tfl.core.crypto.link.LinkRole
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.model.proto.LinkMessage
import app.tfl.core.transport.ReceivedEnvelope
import app.tfl.core.transport.Transport
import app.tfl.core.transport.TransportLog
import app.tfl.core.transport.radio.Radio
import app.tfl.core.transport.radio.RadioEvent
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import app.tfl.core.model.Transport as TransportKind

/**
 * Nearby Connections as a [Transport]. It advertises under a random name that changes every 15
 * minutes, discovers in bursts ([DutyCycle]) and connects to the TFL phones it finds. Once Nearby
 * connects, the private handshake ([LinkHandshake]) decides: a friend gets an encrypted link, and
 * anyone else is disconnected without another word. This phone doesn't ask a failed name again
 * until it changes (up to 15 minutes); connections others ask for always get a handshake, which
 * tells a stranger nothing. Nearby's own authentication and encryption are ignored; it only
 * carries opaque bytes.
 *
 * Everything runs in [scope], which must be single-threaded. [stop] from outside that scope.
 */
class NearbyTransport(
    private val scope: CoroutineScope,
    private val radio: Radio,
    private val handshakes: LinkHandshakes,
    private val keys: TransportKeys,
    private val friends: StateFlow<List<LinkPeer>>,
    private val names: EndpointNames,
    private val demand: StateFlow<DiscoveryDemand>,
    private val clock: DeviceClock,
    private val log: TransportLog,
) : Transport {

    override val kind = TransportKind.NEARBY

    private val linkedFriends = MutableStateFlow<Set<Long>>(emptySet())
    override val reachable: StateFlow<Set<Long>> = linkedFriends.asStateFlow()

    private val incoming = Channel<ReceivedEnvelope>(Channel.UNLIMITED)
    override val received: Flow<ReceivedEnvelope> = incoming.receiveAsFlow()

    /** Timers and failures, handled in the same loop as the radio's events. */
    private sealed interface Internal {
        class StepTimeout(val endpoint: Endpoint, val step: Int) : Internal
        class Retry(val endpointId: String) : Internal
        class Broken(val link: Link) : Internal
        class FriendsChanged(val friends: List<LinkPeer>) : Internal
    }

    private val internal = Channel<Internal>(Channel.UNLIMITED)

    private class Endpoint(val id: String, var role: LinkRole, val name: String) {
        var handshake: LinkHandshake? = null
        var link: Link? = null
        var step = 0
        var timer: Job? = null
    }

    private class Link(val peer: LinkPeer, val endpointId: String, val channel: LinkChannel) {
        /** Frames must leave in the order they're sealed. */
        val sending = Mutex()
        var closed = false

        fun close() {
            if (closed) return
            closed = true
            channel.close()
        }
    }

    private val endpoints = HashMap<String, Endpoint>()
    private val links = HashMap<Long, Link>()

    /** Endpoints discovery found, by id, with the name they advertise. */
    private val found = HashMap<String, String>()

    /** Advertised names that failed the handshake or kept failing to connect, until an elapsed time. */
    private val failedUntil = HashMap<String, Long>()
    private val attempts = HashMap<String, Int>()
    private var name = names.next()

    fun start() {
        scope.launch { merge(radio.events, internal.receiveAsFlow()).collect { handle(it) } }
        scope.launch { advertise() }
        scope.launch { discover() }
        scope.launch { friends.drop(1).collect { internal.send(Internal.FriendsChanged(it)) } }
    }

    /** Stops the radio and closes every link. Call it from outside [scope], which it cancels. */
    suspend fun stop() {
        scope.coroutineContext.job.cancelAndJoin()
        withContext(NonCancellable) { radio.stopAll() }
        endpoints.values.toList().forEach(::close)
        endpoints.clear()
        incoming.close()
        publish()
    }

    override suspend fun send(contactId: Long, envelope: ByteArray): Boolean {
        val link = links[contactId] ?: return false
        if (envelope.size + FRAME_OVERHEAD_BYTES > radio.maxPayloadBytes) {
            log.event { "Envelope of ${envelope.size} bytes is too large for one payload" }
            return false
        }
        return link.sending.withLock {
            if (link.closed) return@withLock false
            val frame = link.channel.sealEnvelope(envelope)
            val sent = radio.send(link.endpointId, frame)
            if (sent) log.event { "Sent a ${frame.size}-byte frame to friend #$contactId" } else internal.send(Internal.Broken(link))
            sent
        }
    }

    private suspend fun advertise() {
        while (true) {
            name = names.next()
            radio.stopAdvertising()
            if (radio.startAdvertising(name)) log.event { "Advertising under a new name" } else log.event { "Advertising didn't start" }
            delay(EndpointNames.ROTATION_MILLIS)
        }
    }

    private suspend fun discover() {
        while (true) {
            val plan = DutyCycle.plan(demand.value)
            if (radio.startDiscovery()) {
                log.event { "Discovering for ${plan.onMillis / 1_000} s" }
                delay(plan.onMillis)
                radio.stopDiscovery()
            }
            // Resting; a message waiting for a friend out of reach cuts the rest short.
            withTimeoutOrNull(plan.offMillis) { demand.first { DutyCycle.plan(it).offMillis < plan.offMillis } }
        }
    }

    private suspend fun handle(event: Any) {
        when (event) {
            is RadioEvent.Found -> {
                found[event.endpointId] = event.name
                connectTo(event.endpointId)
            }
            is RadioEvent.Lost -> {
                // Gone out of range: when it's found again (a friend walking back), it's tried afresh.
                found.remove(event.endpointId)?.let { failedUntil.remove(it) }
                attempts.remove(event.endpointId)
            }
            is RadioEvent.Initiated -> initiated(event.endpointId, event.incoming, event.name)
            is RadioEvent.Connected -> connected(event.endpointId)
            is RadioEvent.ConnectFailed -> {
                endpoints[event.endpointId]?.let { forget(it, "connection failed") }
                retryLater(event.endpointId)
            }
            is RadioEvent.Disconnected -> endpoints[event.endpointId]?.let { endpoint ->
                // Cut off mid-handshake: most likely a phone that doesn't know this one. Not again soon.
                if (endpoint.handshake != null) remember(endpoint.name)
                forget(endpoint, "disconnected")
            }
            is RadioEvent.Received -> frame(event.endpointId, event.bytes)
            is Internal.StepTimeout -> {
                val endpoint = event.endpoint
                if (endpoints[endpoint.id] === endpoint && endpoint.step == event.step) fail(endpoint, "handshake timed out")
            }
            is Internal.Retry -> if (event.endpointId in found) connectTo(event.endpointId)
            is Internal.Broken -> endpoints[event.link.endpointId]?.takeIf { it.link === event.link }?.let { drop(it, "a frame didn't go out") }
            is Internal.FriendsChanged -> friendsChanged(event.friends)
        }
    }

    private suspend fun connectTo(id: String) {
        val name = found[id] ?: return
        if (id in endpoints || failed(name) || endpoints.size >= MAX_ENDPOINTS) return
        // Nobody left to find: every friend this phone may talk to is linked already.
        if (friends.value.none { it.id !in links }) return
        endpoints[id] = Endpoint(id, LinkRole.INITIATOR, name)
        if (!radio.requestConnection(this.name, id)) {
            endpoints.remove(id)
            retryLater(id)
        }
    }

    private fun retryLater(id: String) {
        val tries = (attempts[id] ?: 0) + 1
        attempts[id] = tries
        if (tries > MAX_CONNECT_ATTEMPTS) {
            attempts.remove(id)
            found[id]?.let(::remember)
            return
        }
        // Both phones asking at once is the usual cause; waiting different times breaks the tie.
        scope.launch {
            delay(RETRY_MILLIS * tries + Random.nextLong(RETRY_JITTER_MILLIS))
            internal.send(Internal.Retry(id))
        }
    }

    private suspend fun initiated(id: String, incoming: Boolean, name: String) {
        val endpoint = endpoints[id]
        when {
            endpoint != null -> {
                if (endpoint.handshake != null || endpoint.link != null) return
                endpoint.role = if (incoming) LinkRole.RESPONDER else LinkRole.INITIATOR
            }
            // Even a name that failed here before gets a handshake: it may be a friend just added.
            incoming && endpoints.size < MAX_ENDPOINTS -> endpoints[id] = Endpoint(id, LinkRole.RESPONDER, name)
            else -> {
                radio.rejectConnection(id)
                return
            }
        }
        if (!radio.acceptConnection(id)) endpoints[id]?.let { forget(it, "couldn't accept") }
    }

    private suspend fun connected(id: String) {
        val endpoint = endpoints[id] ?: run {
            radio.disconnect(id)
            return
        }
        attempts.remove(id)
        val handshake = handshakes.start(endpoint.role, keys, friends.value)
        endpoint.handshake = handshake
        armTimer(endpoint)
        if (!radio.send(id, handshake.hello)) drop(endpoint, "hello didn't go out")
    }

    private suspend fun frame(id: String, bytes: ByteArray) {
        val endpoint = endpoints[id] ?: return
        val handshake = endpoint.handshake
        if (handshake != null) {
            when (val progress = handshake.receive(bytes)) {
                is LinkProgress.Continue -> {
                    armTimer(endpoint)
                    sendAll(endpoint, progress.send)
                }
                is LinkProgress.Linked -> if (sendAll(endpoint, progress.send)) linked(endpoint, handshake, progress)
                is LinkProgress.Failed -> fail(endpoint, "handshake failed: ${progress.reason}")
            }
            return
        }
        val link = endpoint.link ?: return
        val message = link.channel.open(bytes)?.let(::parse)
        when {
            message == null -> drop(endpoint, "unreadable frame")
            message.hasEnvelope() -> {
                log.event { "Received a ${bytes.size}-byte frame from friend #${link.peer.id}" }
                incoming.send(ReceivedEnvelope(link.peer.id, message.envelope.toByteArray(), kind, clock.currentTimeMillis()))
            }
            message.hasBye() -> drop(endpoint, "friend closed the link")
            else -> drop(endpoint, "unexpected frame")
        }
    }

    /** Sends handshake frames in order; false (and the endpoint dropped) if one doesn't go out. */
    private suspend fun sendAll(endpoint: Endpoint, frames: List<ByteArray>): Boolean {
        for (frame in frames) {
            if (!radio.send(endpoint.id, frame)) {
                drop(endpoint, "handshake frame didn't go out")
                return false
            }
        }
        return true
    }

    private suspend fun linked(endpoint: Endpoint, handshake: LinkHandshake, progress: LinkProgress.Linked) {
        endpoint.timer?.cancel()
        endpoint.handshake = null
        handshake.close()
        val peer = progress.peer
        // The same friend on another endpoint (their name changed): the new link replaces the old.
        links[peer.id]?.let { old -> endpoints[old.endpointId]?.takeIf { it !== endpoint }?.let { drop(it, "replaced by a newer link") } }
        val link = Link(peer, endpoint.id, progress.channel)
        endpoint.link = link
        links[peer.id] = link
        publish()
        log.event { "Linked with friend #${peer.id} as ${endpoint.role.name.lowercase()}" }
    }

    private suspend fun friendsChanged(current: List<LinkPeer>) {
        // Someone new may be a friend now: strangers turned away get another chance.
        failedUntil.clear()
        attempts.clear()
        val allowed = current.associateBy { it.id }
        for (link in links.values.toList()) {
            val now = allowed[link.peer.id]
            if (now == null || !now.keys.signPublicKey.contentEquals(link.peer.keys.signPublicKey)) {
                endpoints[link.endpointId]?.let { drop(it, "friend #${link.peer.id} is no longer allowed") }
            }
        }
        found.keys.toList().forEach { connectTo(it) }
    }

    private fun armTimer(endpoint: Endpoint) {
        endpoint.timer?.cancel()
        val step = ++endpoint.step
        endpoint.timer = scope.launch {
            delay(STEP_TIMEOUT_MILLIS)
            internal.send(Internal.StepTimeout(endpoint, step))
        }
    }

    private fun failed(name: String): Boolean {
        val until = failedUntil[name] ?: return false
        if (clock.elapsedRealtime() < until) return true
        failedUntil.remove(name)
        return false
    }

    /** Not asked again while it keeps this name (names change every 15 minutes). */
    private fun remember(name: String) {
        failedUntil[name] = clock.elapsedRealtime() + FAILURE_MEMORY_MILLIS
    }

    /** A stranger, a failed handshake or a broken stream: disconnect silently and don't retry soon. */
    private suspend fun fail(endpoint: Endpoint, reason: String) {
        remember(endpoint.name)
        drop(endpoint, reason)
    }

    private suspend fun drop(endpoint: Endpoint, reason: String) {
        forget(endpoint, reason)
        radio.disconnect(endpoint.id)
    }

    private fun forget(endpoint: Endpoint, reason: String) {
        if (endpoints[endpoint.id] === endpoint) endpoints.remove(endpoint.id)
        close(endpoint)
        log.event { "Endpoint closed: $reason" }
    }

    private fun close(endpoint: Endpoint) {
        endpoint.timer?.cancel()
        endpoint.handshake?.close()
        endpoint.handshake = null
        endpoint.link?.let { link ->
            if (links[link.peer.id] === link) links.remove(link.peer.id)
            link.close()
        }
        endpoint.link = null
        publish()
    }

    private fun publish() {
        linkedFriends.value = links.keys.toSet()
    }

    private fun parse(bytes: ByteArray): LinkMessage? = try {
        LinkMessage.parseFrom(bytes)
    } catch (e: InvalidProtocolBufferException) {
        null
    }

    companion object {
        /** Nearby's service id: constant, so a scanner can tell TFL is running (and nothing more). */
        const val SERVICE_ID = "app.tfl.link.v1"
        const val STEP_TIMEOUT_MILLIS = 10_000L
        const val FAILURE_MEMORY_MILLIS = 15 * 60_000L
        const val MAX_ENDPOINTS = 8
        const val MAX_CONNECT_ATTEMPTS = 3
        private const val RETRY_MILLIS = 2_000L
        private const val RETRY_JITTER_MILLIS = 3_000L

        /** Link framing around an envelope: the stream's 17 bytes plus protobuf tags, with room to spare. */
        private const val FRAME_OVERHEAD_BYTES = 64
    }
}
