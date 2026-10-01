package app.tfl.core.transport

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.core.model.contact.KeySource
import app.tfl.core.model.contact.VerificationMethod
import app.tfl.core.model.message.MessageKind
import app.tfl.core.model.message.Reaction
import app.tfl.core.model.security.Profile
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.transport.nearby.DutyCycle
import app.tfl.core.transport.outbox.Backoff
import app.tfl.core.transport.radio.Airwaves
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Two (or three) phones in one room, each with the whole stack, on the in-memory radio. */
@RunWith(AndroidJUnit4::class)
class TwoPhonesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val airwaves = Airwaves()
    private val phones = mutableListOf<TestPhone>()

    @After
    fun tearDown() = phones.forEach { it.close() }

    private fun TestScope.phone(name: String) = TestPhone(context, name, airwaves, this).also { phones += it }

    /** Enough virtual time for discovery, the handshake, a send and its receipt. */
    private fun TestScope.settle(millis: Long = 2_000) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private class Friends(val alice: TestPhone, val bob: TestPhone, val bobOnAlice: Long, val aliceOnBob: Long)

    private suspend fun TestScope.friends(bobHasDecoy: Boolean = false): Friends {
        val alice = phone("alice")
        val bob = phone("bob")
        alice.onboard()
        bob.onboard(decoy = bobHasDecoy)
        val friends = Friends(alice, bob, alice.befriend(bob), bob.befriend(alice))
        settle()
        return friends
    }

    private fun queued(outcome: SendOutcome) = checkNotNull((outcome as SendOutcome.Queued).message)

    @Test
    fun `a message goes from Alice to Bob, queued then sent then delivered`() = runTest {
        val f = friends()
        assertEquals(setOf(f.bobOnAlice), f.alice.controller.reachable.value)
        assertTrue(f.alice.service.running)

        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "Meet at the water tower"))
        assertEquals(DeliveryStatus.QUEUED, sent.status)
        settle()

        val received = f.bob.thread(f.aliceOnBob).single()
        assertEquals("Meet at the water tower", received.text)
        assertFalse(received.outgoing)
        assertEquals(Transport.NEARBY, received.transport)
        val delivered = checkNotNull(f.alice.messages.byMsgId(sent.msgId))
        assertEquals(DeliveryStatus.DELIVERED, delivered.status)
        assertEquals(Transport.NEARBY, delivered.transport)
        assertTrue(checkNotNull(delivered.sentAtMillis) <= checkNotNull(delivered.deliveredAtMillis))
        assertTrue(f.alice.outbox.all().isEmpty())
        assertEquals("content and sender hidden by default", listOf<String?>(null), f.bob.alerts.alerts)
    }

    @Test
    fun `out of range it waits, and goes when they meet again`() = runTest {
        val f = friends()
        airwaves.setInRange(f.alice.radio, f.bob.radio, false)
        settle()
        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "Where are you?"))
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        assertEquals(DeliveryStatus.QUEUED, f.alice.messages.byMsgId(sent.msgId)?.status)
        assertTrue(f.bob.thread(f.aliceOnBob).isEmpty())

        airwaves.setInRange(f.alice.radio, f.bob.radio, true)
        // A message waits, so Alice looks every minute.
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis + 2_000)
        assertEquals(DeliveryStatus.DELIVERED, f.alice.messages.byMsgId(sent.msgId)?.status)
        assertEquals(listOf("Where are you?"), f.bob.thread(f.aliceOnBob).map { it.text })
    }

    @Test
    fun `replies, reactions, edits and deletes reach the other phone`() = runTest {
        val f = friends()
        val question = queued(f.alice.messenger.sendText(f.bobOnAlice, "Tower at 5?"))
        settle()
        val onBob = f.bob.thread(f.aliceOnBob).single()
        f.bob.messenger.sendText(f.aliceOnBob, "Yes", replyTo = onBob.msgId)
        f.bob.messenger.react(onBob.id, "👍")
        settle()
        val reply = f.alice.thread(f.bobOnAlice).last()
        assertEquals(question.msgId, reply.replyTo)
        assertEquals(listOf(Reaction(fromMe = false, emoji = "👍")), f.alice.thread(f.bobOnAlice).first().reactions)

        f.alice.messenger.edit(question.id, "Tower at 6?")
        settle()
        val edited = f.bob.thread(f.aliceOnBob).first()
        assertEquals("Tower at 6?", edited.text)
        assertTrue(edited.edited)

        f.alice.messenger.deleteForEveryone(question.id)
        settle()
        val gone = f.bob.thread(f.aliceOnBob).first()
        assertTrue(gone.deleted)
        assertNull(gone.text)
        assertTrue("reactions go with it", gone.reactions.isEmpty())
    }

    @Test
    fun `only your own messages can be edited, and only for 15 minutes`() = runTest {
        val f = friends()
        val mine = queued(f.alice.messenger.sendText(f.bobOnAlice, "draft"))
        settle()
        val theirs = f.bob.thread(f.aliceOnBob).single()
        assertEquals(SendOutcome.Refused(SendRefusal.NOT_ALLOWED), f.bob.messenger.edit(theirs.id, "hacked"))
        assertEquals(SendOutcome.Refused(SendRefusal.NOT_ALLOWED), f.bob.messenger.deleteForEveryone(theirs.id))
        advanceTimeBy(15 * 60_000L + 1)
        assertEquals(SendOutcome.Refused(SendRefusal.NOT_ALLOWED), f.alice.messenger.edit(mine.id, "too late"))
    }

    @Test
    fun `the disappearing timer syncs, and messages vanish on both phones`() = runTest {
        val f = friends()
        f.alice.messenger.setTimer(f.bobOnAlice, 300)
        settle()
        val onBob = checkNotNull(f.bob.conversations.forContact(f.aliceOnBob))
        assertEquals(300, onBob.expiresAfterSeconds)
        assertEquals(listOf(MessageKind.TIMER_CHANGED), f.bob.thread(f.aliceOnBob).map { it.kind })

        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "burn after reading"))
        assertEquals(300, sent.expiresAfterSeconds)
        settle()
        val arrived = f.bob.thread(f.aliceOnBob).last()
        assertEquals("burn after reading", arrived.text)
        assertEquals(300, arrived.expiresAfterSeconds)

        settle(300_000)
        assertTrue(f.alice.thread(f.bobOnAlice).none { it.kind == MessageKind.TEXT })
        assertTrue(f.bob.thread(f.aliceOnBob).none { it.kind == MessageKind.TEXT })
        assertNull("erased, not just hidden", f.bob.messages.byMsgId(arrived.msgId))
        assertNull(f.alice.messages.byMsgId(sent.msgId))
    }

    @Test
    fun `a scheduled message goes at its time`() = runTest {
        val f = friends()
        val hour = 60 * 60_000L
        val scheduled = queued(f.alice.messenger.sendText(f.bobOnAlice, "Happy birthday!", scheduleAtMillis = f.alice.clock.currentTimeMillis() + hour))
        settle(hour - 60_000)
        assertTrue(f.bob.thread(f.aliceOnBob).isEmpty())
        assertEquals(DeliveryStatus.QUEUED, f.alice.messages.byMsgId(scheduled.msgId)?.status)
        assertEquals("WorkManager wakes the outbox at its time", scheduled.createdAtMillis, f.alice.wakeup.at)

        settle(60_000 + 2_000)
        assertEquals(listOf("Happy birthday!"), f.bob.thread(f.aliceOnBob).map { it.text })
        assertEquals(DeliveryStatus.DELIVERED, f.alice.messages.byMsgId(scheduled.msgId)?.status)
        assertNull("nothing else scheduled", f.alice.wakeup.at)
    }

    @Test
    fun `blocked friends get nothing and can't be messaged`() = runTest {
        val f = friends()
        f.bob.fixture.contacts.setBlocked(f.aliceOnBob, true)
        settle()
        assertTrue("the link is dropped", f.alice.controller.reachable.value.isEmpty())

        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "hello?"))
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        assertEquals(DeliveryStatus.QUEUED, f.alice.messages.byMsgId(sent.msgId)?.status)
        assertTrue(f.bob.thread(f.aliceOnBob).isEmpty())
        assertEquals(SendOutcome.Refused(SendRefusal.BLOCKED), f.bob.messenger.sendText(f.aliceOnBob, "hi"))
        assertTrue(f.bob.outbox.all().isEmpty())
    }

    @Test
    fun `a stranger nearby learns nothing and gets nothing`() = runTest {
        val f = friends()
        val carol = phone("carol")
        carol.onboard()
        carol.befriend(f.alice) // Carol has Alice's key; Alice has never heard of Carol
        settle(DutyCycle.IDLE.onMillis + DutyCycle.IDLE.offMillis)
        assertTrue(carol.controller.reachable.value.isEmpty())
        assertEquals(setOf(f.bobOnAlice), f.alice.controller.reachable.value)

        f.alice.messenger.sendText(f.bobOnAlice, "just for Bob")
        settle()
        assertEquals(1, f.bob.thread(f.aliceOnBob).size)
        assertTrue(carol.thread(carol.contactIdOf(f.alice)).isEmpty())
    }

    @Test
    fun `while Bob is locked, messages still arrive, sealed, and show at unlock`() = runTest {
        val f = friends()
        f.bob.lock()
        settle()
        assertEquals("keys kept: stay reachable is on", setOf(f.aliceOnBob), f.bob.controller.reachable.value)

        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "Are you awake?"))
        settle()
        assertEquals("the locked phone acknowledges", DeliveryStatus.DELIVERED, f.alice.messages.byMsgId(sent.msgId)?.status)
        assertEquals(listOf<String?>(null), f.bob.alerts.alerts)
        assertEquals(1, f.bob.inbox.readAll().size)

        f.bob.unlock()
        settle()
        assertEquals(listOf("Are you awake?"), f.bob.thread(f.aliceOnBob).map { it.text })
        assertTrue("filed and erased", f.bob.inbox.readAll().isEmpty())
    }

    @Test
    fun `what Bob sends while locked is recorded at unlock`() = runTest {
        val f = friends()
        airwaves.setInRange(f.alice.radio, f.bob.radio, false)
        settle()
        val sent = queued(f.bob.messenger.sendText(f.aliceOnBob, "Leaving now"))
        f.bob.lock()
        airwaves.setInRange(f.alice.radio, f.bob.radio, true)
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis + 2_000)
        assertEquals(listOf("Leaving now"), f.alice.thread(f.bobOnAlice).map { it.text })

        f.bob.unlock()
        settle()
        val onBob = checkNotNull(f.bob.messages.byMsgId(sent.msgId))
        assertEquals(DeliveryStatus.DELIVERED, onBob.status)
        assertTrue(f.bob.outbox.all().isEmpty())
    }

    @Test
    fun `without stay reachable, locking stops Nearby and wipes its keys`() = runTest {
        val f = friends()
        f.bob.fixture.settings.set(SettingKeys.STAY_REACHABLE, false)
        settle()
        f.bob.lock()
        settle()
        assertNull(f.bob.fixture.keyring.keys.value)
        assertFalse(f.bob.service.running)
        assertTrue(f.alice.controller.reachable.value.isEmpty())

        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "ping"))
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        assertEquals(DeliveryStatus.QUEUED, f.alice.messages.byMsgId(sent.msgId)?.status)

        f.bob.unlock()
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis + 2_000)
        assertEquals(DeliveryStatus.DELIVERED, f.alice.messages.byMsgId(sent.msgId)?.status)
    }

    @Test
    fun `stopping from the notification while locked wipes the keys until the next unlock`() = runTest {
        val f = friends()
        f.bob.lock()
        settle()
        f.bob.controller.stopFromNotification()
        settle()
        assertNull(f.bob.fixture.keyring.keys.value)
        assertFalse(f.bob.service.running)
        f.bob.unlock()
        settle()
        assertTrue(f.bob.service.running)
    }

    @Test
    fun `the decoy runs as the decoy, and the real profile's messages wait for it`() = runTest {
        val f = friends(bobHasDecoy = true)
        f.bob.lock()
        settle()
        f.alice.messenger.sendText(f.bobOnAlice, "first")
        settle()
        assertEquals(1, f.bob.inbox.readAll().size)

        f.bob.unlock(SessionFixture.DURESS_PIN)
        settle()
        assertEquals(Profile.DECOY, f.bob.fixture.session.profile)
        assertTrue("the decoy isn't Alice's friend", f.alice.controller.reachable.value.isEmpty())
        assertTrue("the decoy has no friends", f.bob.fixture.contacts.contacts.first().isEmpty())
        assertEquals("the real profile's record stays", 1, f.bob.inbox.readAll().size)

        val second = queued(f.alice.messenger.sendText(f.bobOnAlice, "second"))
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        assertEquals(DeliveryStatus.QUEUED, f.alice.messages.byMsgId(second.msgId)?.status)

        f.bob.lock()
        f.bob.unlock()
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis + 2_000)
        assertEquals(listOf("first", "second"), f.bob.thread(f.aliceOnBob).map { it.text })
        assertTrue(f.bob.inbox.readAll().isEmpty())
    }

    @Test
    fun `a lost receipt means a resend, and the resend is shown once`() = runTest {
        val f = friends()
        f.bob.faults.dropNext = 1 // Bob's receipt never arrives
        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "once"))
        settle()
        assertEquals(DeliveryStatus.SENT, f.alice.messages.byMsgId(sent.msgId)?.status)

        settle(Backoff.afterSend(1) + DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        assertEquals(DeliveryStatus.DELIVERED, f.alice.messages.byMsgId(sent.msgId)?.status)
        assertEquals(listOf("once"), f.bob.thread(f.aliceOnBob).map { it.text })
    }

    @Test
    fun `a replayed envelope is acknowledged but shown once, even after a restart`() = runTest {
        val f = friends()
        val sent = queued(f.alice.messenger.sendText(f.bobOnAlice, "only once"))
        val original = f.alice.outbox.all().single()
        settle()
        assertEquals(1, f.bob.thread(f.aliceOnBob).size)

        // Alice's phone sends the same envelope again. (Her own outbox won't re-queue an id Bob
        // acknowledged, so her transport restarts first, forgetting that.)
        f.alice.fixture.settings.set(SettingKeys.STAY_REACHABLE, false)
        settle()
        suspend fun replay() {
            f.alice.lock()
            settle()
            f.alice.unlock()
            f.alice.outbox.enqueue(sent.msgId, f.bobOnAlice, original.envelope, original.sealedFor, original.createdAtMillis)
            f.alice.messenger.tidy(f.alice.clock.currentTimeMillis()) // any change hands the queue over
            settle(DutyCycle.WAITING.onMillis)
        }
        replay()
        assertEquals(1, f.bob.thread(f.aliceOnBob).size)
        assertTrue("acknowledged again", f.alice.outbox.all().isEmpty())

        // Bob's transport restarts (keys wiped at lock), remembering ids only from his database.
        f.bob.fixture.settings.set(SettingKeys.STAY_REACHABLE, false)
        settle()
        f.bob.lock()
        settle()
        assertNull(f.bob.fixture.keyring.keys.value)
        f.bob.unlock()
        settle(DutyCycle.IDLE.onMillis + DutyCycle.IDLE.offMillis)
        replay()
        assertEquals(1, f.bob.thread(f.aliceOnBob).size)
        assertEquals(listOf<String?>(null), f.bob.alerts.alerts)
    }

    @Test
    fun `a key change stops sending until verified, then queued messages are sealed to the new key`() = runTest {
        val f = friends()
        airwaves.setInRange(f.alice.radio, f.bob.radio, false)
        settle()
        val waiting = queued(f.alice.messenger.sendText(f.bobOnAlice, "for the old Bob"))

        // Bob reinstalls: a new identity on a new phone, which pairs with Alice again.
        val newBob = phone("newbob")
        newBob.onboard()
        airwaves.setInRange(f.alice.radio, newBob.radio, false)
        val aliceOnNewBob = newBob.befriend(f.alice)
        f.alice.fixture.contacts.changeKeys(f.bobOnAlice, newBob.identityKeys(), KeySource.IN_PERSON_SCAN, f.alice.clock.currentTimeMillis())
        assertEquals(SendOutcome.Refused(SendRefusal.KEY_CHANGED), f.alice.messenger.sendText(f.bobOnAlice, "hi"))

        airwaves.setInRange(f.alice.radio, newBob.radio, true)
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        assertTrue("unverified: no link", f.alice.controller.reachable.value.isEmpty())

        f.alice.fixture.contacts.markVerified(f.bobOnAlice, VerificationMethod.SAFETY_NUMBER_QR, f.alice.clock.currentTimeMillis())
        settle(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis + 2_000)
        assertEquals(listOf("for the old Bob"), newBob.thread(aliceOnNewBob).map { it.text })
        assertEquals(DeliveryStatus.DELIVERED, f.alice.messages.byMsgId(waiting.msgId)?.status)
    }

    @Test
    fun `alerts name the sender only when asked, and stay quiet for the open conversation`() = runTest {
        val f = friends()
        f.bob.fixture.settings.set(SettingKeys.NOTIFY_SHOW_SENDER, true)
        settle()
        f.alice.messenger.sendText(f.bobOnAlice, "one")
        settle()
        assertEquals(listOf<String?>("alice"), f.bob.alerts.alerts)

        f.bob.presence.visibleContact.value = f.aliceOnBob
        f.alice.messenger.sendText(f.bobOnAlice, "two")
        settle()
        assertEquals(1, f.bob.alerts.alerts.size)
        assertEquals(2, f.bob.thread(f.aliceOnBob).size)
    }
}
