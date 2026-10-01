package app.tfl.core.transport

import app.tfl.core.common.log.TflLog
import app.tfl.core.crypto.envelope.EnvelopeCodec
import app.tfl.core.crypto.envelope.OpenResult
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.inbox.LockedInboxStore
import app.tfl.core.crypto.link.LinkHandshakes
import app.tfl.core.crypto.link.LinkPeer
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.DatabaseLockedException
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.OutboxRepository
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.contact.Contact
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.KeyId
import app.tfl.core.model.message.MessageBody
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.message.OutboxItem
import app.tfl.core.model.proto.InboxRecord
import app.tfl.core.model.proto.inboxRecord
import app.tfl.core.model.proto.receivedEnvelope
import app.tfl.core.model.proto.sentEnvelope
import app.tfl.core.transport.di.TransportIo
import app.tfl.core.transport.nearby.DiscoveryDemand
import app.tfl.core.transport.nearby.EndpointNames
import app.tfl.core.transport.nearby.NearbyTransport
import app.tfl.core.transport.outbox.Backoff
import app.tfl.core.transport.outbox.Outbox
import app.tfl.core.transport.outbox.QueuedEnvelope
import app.tfl.core.transport.radio.Radio
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import app.tfl.core.model.Transport as TransportKind

/** A friend the transport may talk to: not blocked, and no unverified key change. */
class Friend(val id: Long, val name: String, val keys: ContactKeys, val keyId: KeyId)

/** What every engine needs besides its keys. */
class EngineDeps @Inject constructor(
    val holder: DatabaseHolder,
    val codec: EnvelopeCodec,
    val handshakes: LinkHandshakes,
    val names: EndpointNames,
    val messenger: Messenger,
    val identities: IdentityRepository,
    val contacts: ContactRepository,
    val outbox: OutboxRepository,
    val feed: OutboxFeed,
    val settings: SettingsRepository,
    val inbox: LockedInboxStore,
    val clock: DeviceClock,
    val alerts: MessageAlerts,
    val wakeup: ScheduledWakeup,
    val presence: ChatPresence,
    val log: TransportLog,
    @param:TransportIo val io: CoroutineDispatcher,
)

/**
 * The transport for one identity, while its keys are held (see TransportKeyring): links with
 * friends, the outbox at work, duplicates and receipts.
 *
 * While the identity's own profile is unlocked, what arrives goes into its database through
 * [Messenger]. While TFL is locked it works from memory (the friends and queue as of the last
 * unlock) and keeps what arrives, still sealed, in the locked inbox; the next unlock files it.
 * It never writes plaintext anywhere but the unlocked database.
 *
 * Runs in [scope], which must be single-threaded.
 */
class TransportEngine(
    private val scope: CoroutineScope,
    private val keys: TransportKeys,
    private val deps: EngineDeps,
) {
    private val myKeyId = deps.codec.keyId(keys.signPublicKey)
    private val friends = MutableStateFlow<List<Friend>>(emptyList())
    private val peers: StateFlow<List<LinkPeer>> =
        friends.map { list -> list.map { LinkPeer(it.id, it.keys) } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Whether this identity's database is open: otherwise, what happens waits in the locked inbox. */
    private var unlocked = false

    /** Held while anything is filed, so the inbox is emptied at unlock before new arrivals go to the database. */
    private val filing = Mutex()
    private var showSender = false

    /** Ids already received, from the database at unlock and since. */
    private val seen = HashSet<MessageId>()
    private val receipts = HashMap<Long, LinkedHashSet<MessageId>>()
    private val receiptFlushes = HashMap<Long, Job>()
    private val outbox = Outbox(scope, deps.clock, ::onSent)
    private val saving = MutableStateFlow(false)

    private var nearby: NearbyTransport? = null
    private val linked = MutableStateFlow<Set<Long>>(emptySet())
    private var wakeupAt: Long? = null

    /** Friends linked right now. */
    val reachable: StateFlow<Set<Long>> = linked.asStateFlow()

    /** Envelopes waiting in memory. */
    val queued: Int get() = outbox.size

    private val demand: StateFlow<DiscoveryDemand> = combine(outbox.waitingFor, linked, saving) { waiting, reachable, saving ->
        DiscoveryDemand(waiting = (waiting - reachable).isNotEmpty(), saving = saving)
    }.stateIn(scope, SharingStarted.Eagerly, DiscoveryDemand())

    fun start() {
        outbox.start()
        scope.launch { followDatabase() }
        scope.launch {
            deps.feed.snapshots.collect { if (it.owner.contentEquals(keys.signPublicKey)) mirror(it.items) }
        }
    }

    /** Looks at the queue now: something may have fallen due while the process slept. */
    fun wake() = outbox.poke()

    private fun mirror(items: List<OutboxItem>) {
        outbox.mirror(items)
        val next = outbox.nextScheduledAt(deps.clock.currentTimeMillis())
        if (next != wakeupAt) {
            wakeupAt = next
            deps.wakeup.set(next)
        }
    }

    fun setSaving(value: Boolean) {
        saving.value = value
    }

    /** Runs Nearby on [radio], or stops it with null. Call from outside the engine's scope. */
    suspend fun setRadio(radio: Radio?) {
        if (radio == null) {
            stopNearby()
            return
        }
        if (nearby != null) return
        val child = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext.job))
        val transport = NearbyTransport(child, radio, deps.handshakes, keys, peers, deps.names, demand, deps.clock, deps.log)
        nearby = transport
        transport.start()
        outbox.attach(transport)
        child.launch { transport.received.collect { onEnvelope(it) } }
        child.launch {
            var before = emptySet<Long>()
            transport.reachable.collect { now ->
                linked.value = now
                (now - before).forEach { flushReceipts(it) }
                before = now
            }
        }
    }

    /** Stops Nearby and forgets what's held in memory. Call from outside the engine's scope. */
    suspend fun stop() {
        stopNearby()
        if (wakeupAt != null) deps.wakeup.set(null)
        outbox.clear()
        seen.clear()
        receipts.clear()
        friends.value = emptyList()
    }

    private suspend fun stopNearby() {
        val transport = nearby ?: return
        nearby = null
        outbox.detach(transport)
        transport.stop()
        linked.value = emptySet()
    }

    private suspend fun followDatabase() {
        deps.holder.database.collectLatest { database ->
            unlocked = false
            if (database == null) return@collectLatest
            try {
                // The transport keys are the last unlocked profile's; another profile's database is left alone.
                val identity = deps.identities.get() ?: return@collectLatest
                if (!identity.signPublicKey.contentEquals(keys.signPublicKey)) return@collectLatest
                filing.withLock {
                    friends.value = allowed(deps.contacts.contacts.first())
                    seen += deps.outbox.seenIds()
                    fileLockedInbox()
                    unlocked = true
                }
                coroutineScope {
                    fun open() = deps.holder.database.value === database
                    launch {
                        deps.contacts.contacts.collect { list ->
                            if (!open()) return@collect
                            friends.value = allowed(list)
                            deps.messenger.resealForNewKeys()
                        }
                    }
                    launch { deps.outbox.items.collect { if (open()) mirror(it) } }
                    launch { deps.settings.observe(SettingKeys.NOTIFY_SHOW_SENDER).collect { if (open()) showSender = it } }
                }
            } catch (e: DatabaseLockedException) {
                // Locked again meanwhile.
            } finally {
                unlocked = false
            }
        }
    }

    private fun allowed(contacts: List<Contact>): List<Friend> =
        contacts.filter { it.canSend }.map { Friend(it.id, it.name, it.keys, deps.codec.keyId(it.keys.signPublicKey)) }

    private suspend fun onEnvelope(envelope: ReceivedEnvelope) {
        try {
            receive(envelope)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TflLog.w(TAG, e) { "An envelope couldn't be handled" }
        }
    }

    private suspend fun receive(envelope: ReceivedEnvelope) = filing.withLock {
        val known = friends.value
        val opened = deps.codec.open(envelope.bytes, keys, { id -> known.firstOrNull { it.keyId == id }?.keys }, envelope.receivedAtMillis)
        if (opened !is OpenResult.Opened) {
            deps.log.event { "Envelope refused: ${(opened as OpenResult.Refused).reason}" }
            return@withLock
        }
        val senderKey = deps.codec.keyId(opened.sender.signPublicKey)
        val sender = known.first { it.keyId == senderKey }
        val body = opened.body
        if (body is MessageBody.Receipt) {
            outbox.acknowledge(sender.id, body.delivered)
            file(opened, sender, envelope)
            return@withLock
        }
        if (opened.msgId !in seen) {
            // Not kept (the locked inbox is full): no receipt, so the friend sends it again later.
            val applied = file(opened, sender, envelope) ?: return@withLock
            seen += opened.msgId
            if (applied == Applied.NEW_MESSAGE && deps.presence.visibleContact.value != sender.id) {
                deps.alerts.newMessage(sender.name.takeIf { showSender })
            }
        }
        queueReceipt(sender.id, opened.msgId)
    }

    /** Files what arrived: into the database while unlocked, otherwise still sealed into the locked inbox. */
    private suspend fun file(opened: OpenResult.Opened, sender: Friend, envelope: ReceivedEnvelope): Applied? {
        if (unlocked) {
            try {
                return deps.messenger.apply(opened, sender.id, envelope.receivedAtMillis, envelope.via)
            } catch (e: DatabaseLockedException) {
                unlocked = false
            }
        }
        val record = inboxRecord {
            ownerKeyId = ByteString.copyFrom(myKeyId.bytes)
            received = receivedEnvelope {
                this.envelope = ByteString.copyFrom(envelope.bytes)
                receivedAtMillis = envelope.receivedAtMillis
                via = envelope.via.name
            }
        }
        if (!withContext(deps.io) { deps.inbox.append(record.toByteArray()) }) return null
        return if (opened.body is MessageBody.Text) Applied.NEW_MESSAGE else Applied.CHANGE
    }

    private suspend fun onSent(item: QueuedEnvelope, atMillis: Long, via: TransportKind): Unit = filing.withLock {
        if (unlocked) {
            try {
                deps.messenger.recordSent(item.msgId, atMillis, via, item.nextAttemptAtMillis)
                return@withLock
            } catch (e: DatabaseLockedException) {
                unlocked = false
            }
        }
        val record = inboxRecord {
            ownerKeyId = ByteString.copyFrom(myKeyId.bytes)
            sent = sentEnvelope {
                msgId = ByteString.copyFrom(item.msgId.bytes)
                sentAtMillis = atMillis
                this.via = via.name
            }
        }
        withContext(deps.io) { deps.inbox.append(record.toByteArray()) }
        Unit
    }

    /**
     * Files what the locked inbox kept for this identity into the database, and leaves any other
     * profile's records for its own unlock. Called holding [filing].
     */
    private suspend fun fileLockedInbox() {
        val records = withContext(deps.io) { deps.inbox.readAll() }
        if (records.isEmpty()) return
        val byKey = friends.value.associateBy { it.keyId }
        val others = ArrayList<ByteArray>()
        var filed = 0
        for (raw in records) {
            val record = parse(raw) ?: continue
            if (!record.ownerKeyId.toByteArray().contentEquals(myKeyId.bytes)) {
                others += raw
                continue
            }
            filed++
            when (record.eventCase) {
                InboxRecord.EventCase.RECEIVED -> {
                    val received = record.received
                    val at = received.receivedAtMillis
                    val opened = deps.codec.open(received.envelope.toByteArray(), keys, { byKey[it]?.keys }, at) as? OpenResult.Opened ?: continue
                    val sender = byKey[deps.codec.keyId(opened.sender.signPublicKey)] ?: continue
                    deps.messenger.apply(opened, sender.id, at, transportOf(received.via))
                }
                InboxRecord.EventCase.SENT -> {
                    val sent = record.sent
                    val at = sent.sentAtMillis
                    deps.messenger.recordSent(MessageId(sent.msgId.toByteArray()), at, transportOf(sent.via), at + Backoff.RECEIPT_WAIT_MILLIS)
                }
                else -> Unit
            }
        }
        withContext(deps.io) { deps.inbox.replaceAll(others) }
        deps.log.event { "Filed $filed records from the locked inbox" }
    }

    private fun queueReceipt(contactId: Long, msgId: MessageId) {
        receipts.getOrPut(contactId) { LinkedHashSet() } += msgId
        if (receiptFlushes[contactId]?.isActive == true) return
        // A short wait gathers a burst of messages into one receipt.
        receiptFlushes[contactId] = scope.launch {
            delay(RECEIPT_BATCH_MILLIS)
            flushReceipts(contactId)
        }
    }

    /** Receipts are sealed and signed like messages, never acknowledged, and not kept: a lost one means a resend. */
    private suspend fun flushReceipts(contactId: Long) {
        val pending = receipts[contactId] ?: return
        val friend = friends.value.firstOrNull { it.id == contactId } ?: run {
            receipts.remove(contactId)
            return
        }
        val transport = nearby?.takeIf { contactId in it.reachable.value } ?: return
        while (pending.isNotEmpty()) {
            val batch = pending.take(MessageLimits.MAX_RECEIPT_IDS)
            val sealed = deps.codec.seal(keys, friend.keys, MessageBody.Receipt(batch), deps.clock.currentTimeMillis())
            if (!transport.send(contactId, sealed.bytes)) return
            pending.removeAll(batch.toSet())
        }
        receipts.remove(contactId)
    }

    private fun parse(bytes: ByteArray): InboxRecord? = try {
        InboxRecord.parseFrom(bytes)
    } catch (e: InvalidProtocolBufferException) {
        null
    }

    private fun transportOf(name: String) = TransportKind.entries.firstOrNull { it.name == name } ?: TransportKind.NEARBY

    private companion object {
        const val TAG = "TransportEngine"
        const val RECEIPT_BATCH_MILLIS = 300L
    }
}
