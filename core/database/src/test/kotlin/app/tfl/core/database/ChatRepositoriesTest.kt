package app.tfl.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.database.repository.OutboxRepository
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.ChatMessage
import app.tfl.core.model.message.KeyId
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageKind
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.message.Reaction
import app.tfl.core.testing.database.PlainDatabaseFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ChatRepositoriesTest {

    private val holder = DatabaseHolder(PlainDatabaseFactory(ApplicationProvider.getApplicationContext()))
    private val contacts = ContactRepository(holder)
    private val conversations = ConversationRepository(holder)
    private val messages = MessageRepository(holder)
    private val outbox = OutboxRepository(holder)
    private val t0 = 1_790_000_000_000L
    private val minute = 60_000L
    private var nextId = 0
    private val keyIdA = KeyId(ByteArray(KeyId.SIZE) { 0xA })
    private val keyIdB = KeyId(ByteArray(KeyId.SIZE) { 0xB })

    private fun keys(seed: Int) = ContactKeys(Random(seed).nextBytes(32), Random(seed + 1_000).nextBytes(32), "%032X".format(seed))

    private fun id() = MessageId(ByteArray(MessageId.SIZE) { (++nextId * 31 + it).toByte() })

    /** A friend and the conversation with them. */
    private suspend fun friend(name: String, seed: Int): Long {
        val contact = contacts.recordPairing(name, keys(seed), mutual = true, nowMillis = t0)
        return conversations.getOrCreate(contact.id, t0).id
    }

    private suspend fun send(conversation: Long, text: String, at: Long, timer: Int = 0, scheduledFor: Long? = null, msgId: MessageId = id()) =
        messages.addOutgoing(conversation, msgId, text, replyTo = null, createdAtMillis = scheduledFor ?: at, expiresAfterSeconds = timer, scheduledForMillis = scheduledFor)

    private suspend fun receive(conversation: Long, text: String, writtenAt: Long, arrivedAt: Long, timer: Int = 0, msgId: MessageId = id()) =
        messages.addIncoming(conversation, msgId, text, replyTo = null, createdAtMillis = writtenAt, receivedAtMillis = arrivedAt, expiresAfterSeconds = timer, transport = Transport.NEARBY)

    private suspend fun shown(conversation: Long, now: Long): List<ChatMessage> = messages.messages(conversation, now).first()

    private fun count(table: String): Int = holder.require().openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    @Before
    fun open() = holder.open("tfl-chats-test.db", ByteArray(32) { 5 })

    @After
    fun tearDown() = holder.close()

    @Test
    fun `each friend has one conversation`() = runTest {
        val alice = contacts.recordPairing("Alice", keys(1), mutual = true, nowMillis = t0)
        val first = conversations.getOrCreate(alice.id, t0)
        assertEquals(first, conversations.getOrCreate(alice.id, t0 + 5))
        assertEquals(first, conversations.forContact(alice.id))
        assertEquals(1, count("conversations"))
    }

    @Test
    fun `messages sit in the order this phone saw them, whatever the other clock says`() = runTest {
        val alice = friend("Alice", 1)
        // Alice's clock runs 5 minutes fast: her "hi" is stamped after the reply it came before.
        receive(alice, "hi", writtenAt = t0 + 5 * minute, arrivedAt = t0)
        send(alice, "hello!", at = t0 + minute)
        assertEquals(listOf("hi", "hello!"), shown(alice, t0 + 2 * minute).map { it.text })
        val hi = shown(alice, t0 + 2 * minute).first()
        assertEquals(t0 + 5 * minute, hi.createdAtMillis)
        assertEquals(t0, hi.sortAtMillis)
    }

    @Test
    fun `a message id is stored once`() = runTest {
        val alice = friend("Alice", 1)
        val msgId = id()
        assertTrue(receive(alice, "hi", t0, t0, msgId = msgId) != null)
        assertNull("a duplicate", receive(alice, "hi again", t0, t0 + 1, msgId = msgId))
        assertThrows(IllegalStateException::class.java) { runBlocking { send(alice, "mine", t0, msgId = msgId) } }
        assertEquals(listOf("hi"), shown(alice, t0).map { it.text })
    }

    @Test
    fun `delivery moves forward only`() = runTest {
        val alice = friend("Alice", 1)
        val sent = send(alice, "on my way", t0)
        assertEquals(DeliveryStatus.QUEUED, sent.status)

        messages.markSent(listOf(sent.msgId), t0 + 1, Transport.NEARBY)
        messages.markDelivered(alice, listOf(sent.msgId), t0 + 2, Transport.NEARBY)
        messages.markSent(listOf(sent.msgId), t0 + 3, Transport.NEARBY)
        messages.markFailed(listOf(sent.msgId))
        val delivered = checkNotNull(messages.byMsgId(sent.msgId))
        assertEquals(DeliveryStatus.DELIVERED, delivered.status)
        assertEquals(t0 + 1, delivered.sentAtMillis)
        assertEquals(t0 + 2, delivered.deliveredAtMillis)
        assertEquals(Transport.NEARBY, delivered.transport)

        // A receipt can overtake the "sent" report: it's delivered, and it left no later than that.
        val quick = send(alice, "quick", t0)
        messages.markDelivered(alice, listOf(quick.msgId), t0 + 9, Transport.NEARBY)
        assertEquals(t0 + 9, messages.byMsgId(quick.msgId)?.sentAtMillis)

        val lost = send(alice, "lost", t0)
        messages.markFailed(listOf(lost.msgId))
        assertEquals(DeliveryStatus.FAILED, messages.byMsgId(lost.msgId)?.status)

        // A receipt from another conversation's friend changes nothing.
        val bob = friend("Bob", 2)
        val forAlice = send(alice, "for Alice", t0)
        messages.markDelivered(bob, listOf(forAlice.msgId), t0 + 1, Transport.NEARBY)
        assertEquals(DeliveryStatus.QUEUED, messages.byMsgId(forAlice.msgId)?.status)

        // Receipts never change what the friend sent.
        val theirs = checkNotNull(receive(alice, "theirs", t0, t0))
        messages.markDelivered(alice, listOf(theirs.msgId), t0 + 1, Transport.NEARBY)
        assertNull(messages.byMsgId(theirs.msgId)?.status)
    }

    @Test
    fun `only the author edits, within 15 minutes, and later edits win`() = runTest {
        val alice = friend("Alice", 1)
        val bob = friend("Bob", 2)
        val theirs = checkNotNull(receive(alice, "meet at 5", t0, t0))
        val mine = send(alice, "ok", t0)

        assertFalse("not their author", messages.applyEdit(alice, theirs.msgId, outgoing = true, "meet at 6", 1, t0 + minute))
        assertFalse("Alice can't edit my message", messages.applyEdit(alice, mine.msgId, outgoing = false, "no", 1, t0 + minute))
        assertFalse("from another conversation", messages.applyEdit(bob, theirs.msgId, outgoing = false, "meet at 6", 1, t0 + minute))
        assertFalse("too late", messages.applyEdit(alice, theirs.msgId, outgoing = false, "meet at 6", 1, t0 + MessageLimits.EDIT_WINDOW_MILLIS + 1))

        assertTrue(messages.applyEdit(alice, theirs.msgId, outgoing = false, "meet at 7", 2, t0 + 2 * minute))
        assertFalse("an older edit arriving late", messages.applyEdit(alice, theirs.msgId, outgoing = false, "meet at 6", 1, t0 + minute))
        val edited = checkNotNull(messages.byMsgId(theirs.msgId))
        assertEquals("meet at 7", edited.text)
        assertTrue(edited.edited)
        assertEquals(t0 + 2 * minute, edited.editedAtMillis)

        assertTrue(messages.applyEdit(alice, mine.msgId, outgoing = true, "ok!", 1, t0 + MessageLimits.EDIT_WINDOW_MILLIS))
    }

    @Test
    fun `delete for everyone erases the text and reactions, for the author only`() = runTest {
        val alice = friend("Alice", 1)
        val bob = friend("Bob", 2)
        val theirs = checkNotNull(receive(alice, "oops, wrong chat", t0, t0))
        messages.setReaction(alice, theirs.msgId, fromMe = true, emoji = "😮", atMillis = t0)

        assertFalse("I didn't write it", messages.deleteForEveryone(alice, theirs.msgId, outgoing = true))
        assertFalse("from another conversation", messages.deleteForEveryone(bob, theirs.msgId, outgoing = false))
        assertTrue(messages.deleteForEveryone(alice, theirs.msgId, outgoing = false))

        val gone = shown(alice, t0).single()
        assertTrue(gone.deleted)
        assertNull(gone.text)
        assertTrue(gone.reactions.isEmpty())
        assertFalse("no edits after", messages.applyEdit(alice, theirs.msgId, outgoing = false, "back", 1, t0 + 1))
        assertFalse("no reactions after", messages.setReaction(alice, theirs.msgId, fromMe = true, emoji = "👍", atMillis = t0))
    }

    @Test
    fun `delete for me removes the message from this phone`() = runTest {
        val alice = friend("Alice", 1)
        val message = send(alice, "draft thought", t0)
        messages.setReaction(alice, message.msgId, fromMe = false, emoji = "❤️", atMillis = t0)
        messages.deleteForMe(message.id)
        assertTrue(shown(alice, t0).isEmpty())
        assertNull(messages.byMsgId(message.msgId))
        assertEquals(0, count("reactions"))
    }

    @Test
    fun `one reaction each, which can change or go`() = runTest {
        val alice = friend("Alice", 1)
        val bob = friend("Bob", 2)
        val message = checkNotNull(receive(alice, "tower at noon?", t0, t0))
        messages.setReaction(alice, message.msgId, fromMe = false, emoji = "👍", atMillis = t0)
        messages.setReaction(alice, message.msgId, fromMe = true, emoji = "❤️", atMillis = t0)
        messages.setReaction(alice, message.msgId, fromMe = true, emoji = "😂", atMillis = t0 + 1)
        assertEquals(listOf(Reaction(true, "😂"), Reaction(false, "👍")), shown(alice, t0).single().reactions)

        messages.setReaction(alice, message.msgId, fromMe = true, emoji = null, atMillis = t0 + 2)
        assertEquals(listOf(Reaction(false, "👍")), shown(alice, t0).single().reactions)
        assertFalse("from another conversation", messages.setReaction(bob, message.msgId, fromMe = false, emoji = "👎", atMillis = t0))
        assertFalse("an unknown message", messages.setReaction(alice, id(), fromMe = false, emoji = "👎", atMillis = t0))
    }

    @Test
    fun `disappearing messages count down from sending and from arrival, then are erased`() = runTest {
        val alice = friend("Alice", 1)
        val mine = send(alice, "burn after reading", t0, timer = 300)
        val theirs = checkNotNull(receive(alice, "gone soon", writtenAt = t0, arrivedAt = t0 + 10 * minute, timer = 300))
        assertEquals(t0 + 5 * minute, mine.expiresAtMillis)
        assertEquals(t0 + 15 * minute, theirs.expiresAtMillis)
        assertEquals(t0 + 5 * minute, messages.nextChange(t0).first())

        assertEquals(listOf("gone soon"), shown(alice, t0 + 5 * minute).map { it.text })
        assertEquals(t0 + 15 * minute, messages.nextChange(t0 + 5 * minute).first())
        assertTrue(shown(alice, t0 + 15 * minute).isEmpty())
        assertNull(messages.nextChange(t0 + 15 * minute).first())

        assertEquals(2, messages.deleteExpired(t0 + 15 * minute))
        assertEquals(0, count("messages"))
    }

    @Test
    fun `a scheduled message waits in its own list until due`() = runTest {
        val alice = friend("Alice", 1)
        val due = t0 + 60 * minute
        val scheduled = send(alice, "happy birthday", t0, timer = 3_600, scheduledFor = due)
        assertEquals(due, scheduled.createdAtMillis)
        assertEquals(due + 3_600_000, scheduled.expiresAtMillis)
        assertTrue(shown(alice, t0).isEmpty())
        assertEquals(listOf("happy birthday"), messages.scheduled(alice, t0).first().map { it.text })
        assertEquals(due, messages.nextChange(t0).first())

        val moved = checkNotNull(messages.reschedule(scheduled.id, "happy birthday!!", due + minute, scheduled = true))
        assertEquals(due + minute, moved.createdAtMillis)
        assertEquals(due + minute, moved.scheduledForMillis)

        // Once due, it shows in the conversation (queued until it leaves) and leaves the list.
        val at = due + minute
        assertEquals(listOf("happy birthday!!"), shown(alice, at).map { it.text })
        assertTrue(messages.scheduled(alice, at).first().isEmpty())
        messages.markSent(listOf(scheduled.msgId), at + 1, Transport.NEARBY)
        assertNull("sent: no longer changeable", messages.reschedule(scheduled.id, "too late", at + 2, scheduled = true))
    }

    @Test
    fun `sending a scheduled message now`() = runTest {
        val alice = friend("Alice", 1)
        val scheduled = send(alice, "later", t0, scheduledFor = t0 + 60 * minute)
        val now = checkNotNull(messages.reschedule(scheduled.id, "later", t0 + minute, scheduled = false))
        assertNull(now.scheduledForMillis)
        assertEquals(listOf("later"), shown(alice, t0 + minute).map { it.text })
        assertNull("only scheduled messages can be rescheduled", messages.reschedule(send(alice, "now", t0).id, "x", t0, scheduled = true))
    }

    @Test
    fun `the chats list shows the last message and unread counts`() = runTest {
        val alice = friend("Alice", 1)
        val bob = friend("Bob", 2)
        receive(alice, "one", t0, t0 + 1)
        receive(alice, "two", t0, t0 + 2, timer = 300)
        send(bob, "hey Bob", t0 + 3)
        conversations.touch(bob, t0 + 3)
        conversations.touch(alice, t0 + 2)

        val list = conversations.summaries(t0 + 4).first()
        assertEquals(listOf(bob, alice), list.map { it.id })
        assertEquals(listOf(0, 2), list.map { it.unreadCount })
        assertEquals(listOf("hey Bob", "two"), list.map { it.lastMessage?.text })

        // An expired message is neither last nor unread.
        val later = conversations.summaries(t0 + 2 + 5 * minute).first().first { it.id == alice }
        assertEquals("one", later.lastMessage?.text)
        assertEquals(1, later.unreadCount)

        conversations.markRead(alice, t0 + 2)
        conversations.markRead(alice, t0 + 1)
        assertEquals(0, conversations.summaries(t0 + 4).first().first { it.id == alice }.unreadCount)
    }

    @Test
    fun `the timer takes only the offered values, and changes show as lines`() = runTest {
        val alice = friend("Alice", 1)
        conversations.setTimer(alice, 86_400, t0)
        assertEquals(86_400, conversations.get(alice)?.expiresAfterSeconds)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { conversations.setTimer(alice, 42, t0) } }

        val line = id()
        assertTrue(messages.addTimerChange(alice, line, 86_400, t0, receivedAtMillis = null))
        assertFalse("stored once", messages.addTimerChange(alice, line, 86_400, t0, receivedAtMillis = null))
        assertTrue(messages.addTimerChange(alice, id(), 0, t0, receivedAtMillis = t0 + 1))
        val lines = shown(alice, t0 + 2)
        assertEquals(listOf(MessageKind.TIMER_CHANGED, MessageKind.TIMER_CHANGED), lines.map { it.kind })
        assertEquals(listOf(true, false), lines.map { it.outgoing })
        assertEquals(listOf(86_400, 0), lines.map { it.expiresAfterSeconds })
        assertNull(lines.first().expiresAtMillis)
    }

    @Test
    fun `the newest timer change wins on both phones`() = runTest {
        val alice = friend("Alice", 1)
        conversations.setTimer(alice, 300, nowMillis = t0 + 10)
        assertFalse("changed before mine", conversations.applyTimer(alice, 3_600, changedAtMillis = t0 + 5))
        assertEquals(300, conversations.get(alice)?.expiresAfterSeconds)
        assertTrue(conversations.applyTimer(alice, 0, changedAtMillis = t0 + 20))
        assertEquals(0, conversations.get(alice)?.expiresAfterSeconds)

        // A change here always applies, even if Alice's clock ran ahead of this phone's.
        conversations.setTimer(alice, 86_400, nowMillis = t0 + 15)
        assertEquals(86_400, conversations.get(alice)?.expiresAfterSeconds)
        assertEquals(t0 + 21, conversations.get(alice)?.timerChangedAtMillis)
    }

    @Test
    fun `deleting a friend removes their conversation, messages and queued envelopes`() = runTest {
        val alice = friend("Alice", 1)
        val message = checkNotNull(receive(alice, "hi", t0, t0))
        messages.setReaction(alice, message.msgId, fromMe = true, emoji = "👋", atMillis = t0)
        outbox.enqueue(id(), checkNotNull(conversations.get(alice)).contactId, byteArrayOf(1), keyIdA, t0)

        contacts.delete(checkNotNull(conversations.get(alice)).contactId)
        for (table in listOf("conversations", "messages", "reactions", "outbox")) assertEquals(table, 0, count(table))
    }

    @Test
    fun `the outbox keeps sealed envelopes in order until their receipt`() = runTest {
        val aliceConversation = friend("Alice", 1)
        val alice = checkNotNull(conversations.get(aliceConversation)).contactId
        val bob = checkNotNull(conversations.get(friend("Bob", 2))).contactId
        val first = id()
        val second = id()
        val later = id()
        outbox.enqueue(second, alice, byteArrayOf(2), keyIdA, createdAtMillis = t0 + 1)
        outbox.enqueue(first, alice, byteArrayOf(1), keyIdA, createdAtMillis = t0)
        outbox.enqueue(later, bob, byteArrayOf(3), keyIdB, createdAtMillis = t0, notBeforeMillis = t0 + 60 * minute)

        assertEquals(listOf(first, second), outbox.forContact(alice).map { it.msgId })
        assertEquals(t0 + 60 * minute, outbox.forContact(bob).single().nextAttemptAtMillis)

        outbox.markSent(first, atMillis = t0 + 5, nextAttemptAtMillis = t0 + 65)
        val sent = outbox.forContact(alice).first()
        assertEquals(1, sent.attempts)
        assertEquals(t0 + 5, sent.sentAtMillis)
        assertEquals(t0 + 65, sent.nextAttemptAtMillis)

        outbox.replace(later, byteArrayOf(4), keyIdA, createdAtMillis = t0 + minute, notBeforeMillis = 0)
        val replaced = outbox.forContact(bob).single()
        assertArrayEquals(byteArrayOf(4), replaced.envelope)
        assertEquals(keyIdA, replaced.sealedFor)
        assertEquals(0, replaced.nextAttemptAtMillis)

        outbox.delivered(bob, listOf(first))
        assertEquals("Bob can't clear Alice's", 3, outbox.all().size)
        outbox.delivered(alice, listOf(first))
        assertEquals(listOf(second, later), outbox.all().map { it.msgId })
        assertEquals(2, outbox.items.first().size)
    }

    @Test
    fun `the outbox gives up on envelopes older than the cutoff`() = runTest {
        val alice = checkNotNull(conversations.get(friend("Alice", 1))).contactId
        val old = id()
        outbox.enqueue(old, alice, byteArrayOf(1), keyIdA, createdAtMillis = t0)
        outbox.enqueue(id(), alice, byteArrayOf(2), keyIdA, createdAtMillis = t0 + 10)
        assertEquals(listOf(old), outbox.giveUpOlderThan(t0 + 1))
        assertEquals(1, outbox.all().size)
        assertTrue(outbox.giveUpOlderThan(t0 + 1).isEmpty())
    }

    @Test
    fun `a received id is remembered until it expires`() = runTest {
        val msgId = id()
        val sender = KeyId(ByteArray(KeyId.SIZE) { 7 })
        assertTrue(outbox.firstSeen(msgId, sender, keepUntilMillis = t0 + 100))
        assertFalse("a replay", outbox.firstSeen(msgId, sender, keepUntilMillis = t0 + 200))
        assertTrue(outbox.wasSeen(msgId))
        assertEquals(listOf(msgId), outbox.seenIds())

        assertEquals(0, outbox.forgetSeenBefore(t0 + 100))
        assertTrue(outbox.wasSeen(msgId))
        assertEquals(1, outbox.forgetSeenBefore(t0 + 101))
        assertFalse(outbox.wasSeen(msgId))
    }

    @Test
    fun `nothing is readable while locked`() = runTest {
        val alice = friend("Alice", 1)
        send(alice, "hi", t0)
        holder.close()
        assertTrue(conversations.summaries(t0).first().isEmpty())
        assertTrue(messages.messages(alice, t0).first().isEmpty())
        assertTrue(outbox.items.first().isEmpty())
        assertThrows(DatabaseLockedException::class.java) { runBlocking { messages.get(1) } }
        assertThrows(DatabaseLockedException::class.java) { runBlocking { outbox.all() } }
    }
}
