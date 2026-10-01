package app.tfl.core.transport.debug

import app.tfl.core.crypto.envelope.EnvelopeCodec
import app.tfl.core.crypto.envelope.OpenResult
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.link.LinkHandshakes
import app.tfl.core.crypto.link.LinkPeer
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.MessageBody
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.session.di.ApplicationScope
import app.tfl.core.transport.ReceivedEnvelope
import app.tfl.core.transport.TransportLog
import app.tfl.core.transport.di.TransportDispatcher
import app.tfl.core.transport.nearby.DiscoveryDemand
import app.tfl.core.transport.nearby.EndpointNames
import app.tfl.core.transport.nearby.NearbyTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug builds: "Test friend" as a second phone inside this one, on [SimulatedAir]. It runs the
 * real handshake, envelopes and receipts, acknowledges what arrives, and answers each text with an
 * echo, so a single phone can exercise the whole transport; only Nearby's radios are left out.
 * Pair with the Test friend QR first (Settings → Developer), or it's a stranger and gets dropped.
 */
@Singleton
class SimulatedFriend @Inject constructor(
    private val air: SimulatedAir,
    private val derivation: IdentityKeyDerivation,
    private val codec: EnvelopeCodec,
    private val handshakes: LinkHandshakes,
    private val names: EndpointNames,
    private val clock: DeviceClock,
    private val events: TransportEvents,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:TransportDispatcher private val dispatcher: CoroutineDispatcher,
) {
    private val runningState = MutableStateFlow(false)

    /** Whether the simulated friend is in range. */
    val running: StateFlow<Boolean> = runningState.asStateFlow()

    private var session: Session? = null

    private class Session(val scope: CoroutineScope, val transport: NearbyTransport, val keys: TransportKeys)

    /** Brings Test friend (from [seed]) into range; [me] is this phone's identity, the one friend it knows. */
    @Synchronized
    fun start(seed: ByteArray, me: ContactKeys) {
        if (session != null) return
        val keys = derivation.derive(seed).use { it.forTransport() }
        val scope = CoroutineScope(appScope.coroutineContext + SupervisorJob(appScope.coroutineContext.job) + dispatcher)
        val log = TransportLog { message -> events.event { "Simulated friend: ${message()}" } }
        val transport = NearbyTransport(
            CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext.job)),
            air.friend, handshakes, keys, MutableStateFlow(listOf(LinkPeer(ME, me))), names,
            MutableStateFlow(DiscoveryDemand(waiting = true)), clock, log,
        )
        session = Session(scope, transport, keys)
        transport.start()
        val seen = HashSet<MessageId>()
        scope.launch { transport.received.collect { answer(it, transport, keys, me, seen) } }
        runningState.value = true
    }

    @Synchronized
    fun stop() {
        val current = session ?: return
        session = null
        runningState.value = false
        appScope.launch(dispatcher) {
            current.transport.stop()
            current.scope.coroutineContext.job.cancel()
            current.keys.close()
        }
    }

    private suspend fun answer(envelope: ReceivedEnvelope, transport: NearbyTransport, keys: TransportKeys, me: ContactKeys, seen: HashSet<MessageId>) {
        val myId = codec.keyId(me.signPublicKey)
        val opened = codec.open(envelope.bytes, keys, { id -> me.takeIf { id == myId } }, clock.currentTimeMillis()) as? OpenResult.Opened ?: return
        val body = opened.body
        if (body is MessageBody.Receipt) return
        transport.send(ME, codec.seal(keys, me, MessageBody.Receipt(listOf(opened.msgId)), clock.currentTimeMillis()).bytes)
        if (!seen.add(opened.msgId) || body !is MessageBody.Text) return
        delay(ECHO_DELAY_MILLIS)
        val echo = MessageBody.Text("Echo: ${body.text}".take(MessageLimits.MAX_TEXT_CHARS), replyTo = opened.msgId, expiresAfterSeconds = body.expiresAfterSeconds)
        transport.send(ME, codec.seal(keys, me, echo, clock.currentTimeMillis()).bytes)
    }

    private companion object {
        /** The id the simulated friend knows this phone by. */
        const val ME = 1L
        const val ECHO_DELAY_MILLIS = 1_000L
    }
}
