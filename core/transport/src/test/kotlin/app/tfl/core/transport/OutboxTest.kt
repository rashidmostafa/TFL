package app.tfl.core.transport

import app.tfl.core.model.message.KeyId
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.message.OutboxItem
import app.tfl.core.transport.outbox.Backoff
import app.tfl.core.transport.outbox.Outbox
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxTest {

    private val alice = 1L
    private val bob = 2L
    private var nextId = 0
    private val sends = mutableListOf<Pair<MessageId, Int>>()

    private fun id() = MessageId(ByteArray(MessageId.SIZE) { (++nextId).toByte() })

    private fun item(contact: Long, createdAt: Long, notBefore: Long = 0, msgId: MessageId = id(), envelope: ByteArray = msgId.bytes) =
        OutboxItem(msgId, contact, envelope, KeyId(ByteArray(KeyId.SIZE)), createdAt, notBefore, 0, notBefore, null)

    private class Setup(val outbox: Outbox, val transport: FakeTransport, val clock: SchedulerClock)

    private fun TestScope.setup(): Setup {
        val clock = SchedulerClock(testScheduler)
        val outbox = Outbox(backgroundScope, clock) { item, _, _ -> sends += item.msgId to item.attempts }
        val transport = FakeTransport()
        outbox.attach(transport)
        outbox.start()
        return Setup(outbox, transport, clock)
    }

    private fun Setup.sentIds() = transport.sent.map { MessageId(it.second) }

    @Test
    fun `backoff waits for a receipt, then doubles up to 30 minutes`() {
        val waits = (1..10).map { Backoff.afterSend(it) / 1_000 }
        assertEquals(listOf(60L, 60, 60, 80, 160, 320, 640, 1_280, 1_800, 1_800), waits)
    }

    @Test
    fun `envelopes go oldest first, only to friends in reach`() = runTest {
        val s = setup()
        val now = s.clock.currentTimeMillis()
        val second = item(alice, createdAt = now - 1)
        val first = item(alice, createdAt = now - 2)
        val forBob = item(bob, createdAt = now - 3)
        s.outbox.mirror(listOf(second, first, forBob))
        runCurrent()
        assertTrue("nobody in reach", s.transport.sent.isEmpty())
        assertEquals(setOf(alice, bob), s.outbox.waitingFor.value)

        s.transport.reach.value = setOf(alice)
        runCurrent()
        assertEquals(listOf(first.msgId, second.msgId), s.sentIds())
        assertEquals(setOf(bob), s.outbox.waitingFor.value)
    }

    @Test
    fun `without a receipt it's sent again with backoff, and a receipt ends it`() = runTest {
        val s = setup()
        val message = item(alice, createdAt = s.clock.currentTimeMillis())
        s.transport.reach.value = setOf(alice)
        s.outbox.mirror(listOf(message))
        runCurrent()
        assertEquals(1, s.transport.sent.size)

        advanceTimeBy(Backoff.afterSend(1) - 1)
        runCurrent()
        assertEquals("not before the receipt wait", 1, s.transport.sent.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, s.transport.sent.size)
        advanceTimeBy(Backoff.afterSend(2) + Backoff.afterSend(3))
        runCurrent()
        assertEquals("attempts as each send went", listOf(1, 2, 3, 4), sends.map { it.second })

        assertEquals(listOf(message.msgId), s.outbox.acknowledge(alice, listOf(message.msgId)))
        val before = s.transport.sent.size
        advanceTimeBy(Backoff.MAX_MILLIS * 2)
        runCurrent()
        assertEquals(before, s.transport.sent.size)
        assertEquals(0, s.outbox.size)
    }

    @Test
    fun `a receipt from someone else changes nothing`() = runTest {
        val s = setup()
        val message = item(alice, createdAt = s.clock.currentTimeMillis())
        s.outbox.mirror(listOf(message))
        assertTrue(s.outbox.acknowledge(bob, listOf(message.msgId)).isEmpty())
        assertEquals(1, s.outbox.size)
    }

    @Test
    fun `a friend linking anew gets everything waiting at once`() = runTest {
        val s = setup()
        val message = item(alice, createdAt = s.clock.currentTimeMillis())
        s.transport.reach.value = setOf(alice)
        s.outbox.mirror(listOf(message))
        runCurrent()
        s.transport.reach.value = emptySet()
        runCurrent()
        advanceTimeBy(5_000)
        s.transport.reach.value = setOf(alice)
        runCurrent()
        assertEquals("resent on the new link, not after the backoff", 2, s.transport.sent.size)
    }

    @Test
    fun `a failed send holds back the rest for that friend, in order`() = runTest {
        val s = setup()
        val now = s.clock.currentTimeMillis()
        val first = item(alice, createdAt = now - 2)
        val second = item(alice, createdAt = now - 1)
        s.transport.reach.value = setOf(alice)
        s.transport.failing = true
        s.outbox.mirror(listOf(first, second))
        runCurrent()
        assertTrue(s.transport.sent.isEmpty())

        s.transport.failing = false
        s.transport.reach.value = emptySet()
        runCurrent()
        s.transport.reach.value = setOf(alice)
        runCurrent()
        assertEquals(listOf(first.msgId, second.msgId), s.sentIds())
    }

    @Test
    fun `a scheduled envelope waits for its time`() = runTest {
        val s = setup()
        val due = s.clock.currentTimeMillis() + 60 * 60_000
        val scheduled = item(alice, createdAt = due, notBefore = due)
        s.transport.reach.value = setOf(alice)
        s.outbox.mirror(listOf(scheduled))
        runCurrent()
        assertTrue(s.transport.sent.isEmpty())
        assertTrue(s.outbox.waitingFor.value.isEmpty())

        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(listOf(scheduled.msgId), s.sentIds())
    }

    @Test
    fun `envelopes older than 30 days aren't sent`() = runTest {
        val s = setup()
        val old = item(alice, createdAt = s.clock.currentTimeMillis() - MessageLimits.MAX_AGE_MILLIS - 1)
        s.transport.reach.value = setOf(alice)
        s.outbox.mirror(listOf(old))
        runCurrent()
        assertTrue(s.transport.sent.isEmpty())
        assertTrue(s.outbox.waitingFor.value.isEmpty())
    }

    @Test
    fun `the stored queue keeps timing for unchanged envelopes and restarts replaced ones`() = runTest {
        val s = setup()
        val kept = item(alice, createdAt = s.clock.currentTimeMillis())
        s.transport.reach.value = setOf(alice)
        s.outbox.mirror(listOf(kept))
        runCurrent()
        assertEquals(1, s.transport.sent.size)

        // The same queue read back: nothing new to send.
        s.outbox.mirror(listOf(kept))
        runCurrent()
        assertEquals(1, s.transport.sent.size)

        // Sealed again (a friend's new key): it goes at once.
        s.outbox.mirror(listOf(item(alice, createdAt = kept.createdAtMillis, msgId = kept.msgId, envelope = byteArrayOf(9))))
        runCurrent()
        assertEquals(2, s.transport.sent.size)

        // Acknowledged, then an old copy of the queue shows up: it stays gone.
        s.outbox.acknowledge(alice, listOf(kept.msgId))
        s.outbox.mirror(listOf(kept))
        assertEquals(0, s.outbox.size)
    }
}
