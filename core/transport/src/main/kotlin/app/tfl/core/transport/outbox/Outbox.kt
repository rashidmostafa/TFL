package app.tfl.core.transport.outbox

import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.message.OutboxItem
import app.tfl.core.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import app.tfl.core.model.Transport as TransportKind

/** When to send again: after at least the 60 s a receipt may take, then 10 s doubling to 30 min. */
object Backoff {
    const val RECEIPT_WAIT_MILLIS = 60_000L
    const val FIRST_MILLIS = 10_000L
    const val MAX_MILLIS = 30 * 60_000L

    /** How long after the [attempt]-th send (1, 2, …) to send again if no receipt came. */
    fun afterSend(attempt: Int): Long {
        val doubled = FIRST_MILLIS shl (attempt - 1).coerceIn(0, 20)
        return doubled.coerceAtMost(MAX_MILLIS).coerceAtLeast(RECEIPT_WAIT_MILLIS)
    }
}

/** An envelope waiting for its friend, with when to try next. */
class QueuedEnvelope(
    val msgId: MessageId,
    val contactId: Long,
    val envelope: ByteArray,
    val createdAtMillis: Long,
    val notBeforeMillis: Long,
    var attempts: Int = 0,
    var nextAttemptAtMillis: Long = notBeforeMillis,
    var sentAtMillis: Long? = null,
) {
    /** When it may go next. */
    val dueAtMillis: Long get() = maxOf(notBeforeMillis, nextAttemptAtMillis)

    /** Older than 30 days: the friend would refuse it, so it isn't sent. */
    fun expired(nowMillis: Long) = nowMillis - createdAtMillis > MessageLimits.MAX_AGE_MILLIS
}

/**
 * The outbox at work, in memory: each envelope goes to its friend when a transport reaches them,
 * oldest first, and again with [Backoff] until the friend's receipt takes it out. A friend who
 * links anew gets everything waiting at once. What's queued is persisted elsewhere (the database,
 * or the locked inbox while TFL is locked); [onSent] reports each send so it can be recorded.
 *
 * Runs in [scope], which must be single-threaded.
 */
class Outbox(
    private val scope: CoroutineScope,
    private val clock: DeviceClock,
    private val onSent: suspend (QueuedEnvelope, atMillis: Long, via: TransportKind) -> Unit,
) {
    private var queue = LinkedHashMap<MessageId, QueuedEnvelope>()
    private val transports = mutableMapOf<Transport, Job>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Ids receipts removed lately, so a copy of the queue read just before can't bring them back. */
    private val acknowledged = LinkedHashSet<MessageId>()

    private val waiting = MutableStateFlow<Set<Long>>(emptySet())

    /** Friends with envelopes ready to go, whether or not they're reachable. */
    val waitingFor: StateFlow<Set<Long>> = waiting.asStateFlow()

    val size: Int get() = queue.size

    fun start() {
        scope.launch { pump() }
    }

    /** A transport's friends become reachable: whatever waits for a newly linked friend goes now. */
    fun attach(transport: Transport) {
        transports[transport] = scope.launch {
            var before = transport.reachable.value
            linked(before)
            transport.reachable.drop(1).collect { now ->
                linked(now - before)
                before = now
            }
        }
    }

    fun detach(transport: Transport) {
        transports.remove(transport)?.cancel()
    }

    fun reachable(contactId: Long): Boolean = transports.keys.any { contactId in it.reachable.value }

    /**
     * Takes the queue as stored. What this outbox knows about an item (attempts, next try) is kept
     * while its envelope is unchanged; a replaced envelope starts afresh.
     */
    fun mirror(items: List<OutboxItem>) {
        val mirrored = LinkedHashMap<MessageId, QueuedEnvelope>()
        for (item in items) {
            if (item.msgId in acknowledged) continue
            val known = queue[item.msgId]?.takeIf { it.envelope.contentEquals(item.envelope) }
            mirrored[item.msgId] = known ?: QueuedEnvelope(
                msgId = item.msgId,
                contactId = item.contactId,
                envelope = item.envelope,
                createdAtMillis = item.createdAtMillis,
                notBeforeMillis = item.notBeforeMillis,
                attempts = item.attempts,
                nextAttemptAtMillis = item.nextAttemptAtMillis,
                sentAtMillis = item.sentAtMillis,
            )
        }
        queue = mirrored
        poke()
    }

    /**
     * [contactId]'s receipt for [msgIds]: those queued for that friend are done. Returns them; ids
     * queued for anyone else are left alone.
     */
    fun acknowledge(contactId: Long, msgIds: List<MessageId>): List<MessageId> {
        val done = msgIds.filter { queue[it]?.contactId == contactId }
        done.forEach {
            queue.remove(it)
            acknowledged += it
        }
        while (acknowledged.size > MAX_ACKNOWLEDGED) acknowledged.remove(acknowledged.first())
        if (done.isNotEmpty()) poke()
        return done
    }

    fun clear() {
        queue.clear()
        poke()
    }

    /** When the next scheduled envelope falls due, or null. */
    fun nextScheduledAt(nowMillis: Long): Long? = queue.values.map { it.notBeforeMillis }.filter { it > nowMillis }.minOrNull()

    /** Looks at the queue again now. */
    fun poke() {
        wake.trySend(Unit)
    }

    private fun linked(contacts: Set<Long>) {
        if (contacts.isEmpty()) return
        val now = clock.currentTimeMillis()
        queue.values.filter { it.contactId in contacts && it.nextAttemptAtMillis > now }.forEach { it.nextAttemptAtMillis = now }
        poke()
    }

    private suspend fun pump() {
        while (true) {
            sendDue()
            val now = clock.currentTimeMillis()
            waiting.value = queue.values.filter { it.dueAtMillis <= now && !it.expired(now) }.mapTo(HashSet()) { it.contactId }
            // Sleep until the next item falls due, or until something changes.
            val next = queue.values.filter { !it.expired(now) }.map { it.dueAtMillis }.filter { it > now }.minOrNull()
            if (next == null) wake.receive() else withTimeoutOrNull(next - now) { wake.receive() }
        }
    }

    private suspend fun sendDue() {
        val now = clock.currentTimeMillis()
        val due = queue.values.filter { it.dueAtMillis <= now && !it.expired(now) }.sortedBy { it.createdAtMillis }
        val stuck = HashSet<Long>()
        for (item in due) {
            if (item.contactId in stuck || queue[item.msgId] !== item) continue
            val transport = route(item.contactId) ?: continue
            if (!transport.send(item.contactId, item.envelope)) {
                // The link failed: the rest for this friend wait for the next one, in order.
                stuck += item.contactId
                continue
            }
            val at = clock.currentTimeMillis()
            item.attempts += 1
            item.sentAtMillis = at
            item.nextAttemptAtMillis = at + Backoff.afterSend(item.attempts)
            onSent(item, at, transport.kind)
        }
    }

    /** The best transport reaching [contactId] now: Nearby before relays, relays before Tor. */
    private fun route(contactId: Long): Transport? =
        transports.keys.filter { contactId in it.reachable.value }.minByOrNull { it.kind.ordinal }

    private companion object {
        const val MAX_ACKNOWLEDGED = 1_024
    }
}
