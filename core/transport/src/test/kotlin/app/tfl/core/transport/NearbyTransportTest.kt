package app.tfl.core.transport

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.link.LinkHandshakes
import app.tfl.core.crypto.link.LinkPeer
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.proto.LinkFrame
import app.tfl.core.testing.crypto.JvmSodium
import app.tfl.core.transport.nearby.DiscoveryDemand
import app.tfl.core.transport.nearby.DutyCycle
import app.tfl.core.transport.nearby.EndpointNames
import app.tfl.core.transport.nearby.NearbyTransport
import app.tfl.core.transport.radio.Airwaves
import app.tfl.core.transport.radio.InMemoryRadio
import app.tfl.core.transport.radio.Radio
import app.tfl.core.transport.radio.RadioEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyTransportTest {

    private val sodium = JvmSodium.api
    private val derivation = IdentityKeyDerivation(sodium)
    private val fingerprints = Fingerprints(sodium)
    private val airwaves = Airwaves()

    /** A radio that remembers what it saw, for checking what a stranger learns. */
    private class Watched(val inner: InMemoryRadio) : Radio by inner {
        val seen = mutableListOf<RadioEvent>()
        override val events: Flow<RadioEvent> = inner.events.onEach { seen += it }
    }

    private inner class Phone(val name: String, seed: Int) {
        val keys: TransportKeys = derivation.derive(ByteArray(32) { seed.toByte() }).use { it.forTransport() }
        val contact = ContactKeys(keys.signPublicKey, keys.kexPublicKey, fingerprints.of(keys.signPublicKey))
        val radio = Watched(airwaves.radio(name))
        val friends = MutableStateFlow<List<LinkPeer>>(emptyList())
        val demand = MutableStateFlow(DiscoveryDemand())
        val received = mutableListOf<ReceivedEnvelope>()
        lateinit var transport: NearbyTransport

        fun knows(vararg others: Pair<Long, Phone>) {
            friends.value = others.map { (id, phone) -> LinkPeer(id, phone.contact) }
        }

        fun TestScope.start() {
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
            transport = NearbyTransport(scope, radio, LinkHandshakes(sodium), keys, friends, EndpointNames(sodium), demand, SchedulerClock(testScheduler), TransportLog.NONE)
            transport.start()
            backgroundScope.launch { transport.received.collect { received += it } }
        }
    }

    private val alice = Phone("alice", 1)
    private val bob = Phone("bob", 2)
    private val carol = Phone("carol", 3)

    private fun TestScope.startAll(vararg phones: Phone) = phones.forEach { with(it) { start() } }

    /** Long enough for a discovery burst and a handshake. */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun `friends link and carry envelopes both ways`() = runTest {
        alice.knows(7L to bob)
        bob.knows(9L to alice)
        startAll(alice, bob)
        settle()
        assertEquals(setOf(7L), alice.transport.reachable.value)
        assertEquals(setOf(9L), bob.transport.reachable.value)

        assertTrue(alice.transport.send(7, byteArrayOf(1, 2, 3)))
        assertTrue(bob.transport.send(9, byteArrayOf(4)))
        runCurrent()
        assertArrayEquals(byteArrayOf(1, 2, 3), bob.received.single().bytes)
        assertEquals(9L, bob.received.single().contactId)
        assertArrayEquals(byteArrayOf(4), alice.received.single().bytes)
    }

    @Test
    fun `a stranger is dropped silently and not tried again for 15 minutes`() = runTest {
        alice.knows(7L to bob)
        carol.knows(1L to alice) // Carol has Alice's key, but Alice doesn't know Carol
        startAll(alice, carol)
        settle()
        assertTrue(alice.transport.reachable.value.isEmpty())
        assertTrue(carol.transport.reachable.value.isEmpty())
        assertFalse(airwaves.isConnected(alice.radio.inner, carol.radio.inner))

        // All Carol got from Alice: a hello (fresh random values) and, if Alice asked to connect,
        // a knock of random-looking tags. Never an answer, and nothing encrypted or signed.
        val fromAlice = carol.radio.seen.filterIsInstance<RadioEvent.Received>().map { LinkFrame.parseFrom(it.bytes) }
        assertTrue(fromAlice.isNotEmpty())
        assertTrue(fromAlice.all { it.hasHello() || it.hasKnock() })

        val connectionsBefore = carol.radio.seen.count { it is RadioEvent.Initiated }
        advanceTimeBy(NearbyTransport.FAILURE_MEMORY_MILLIS - 60_000)
        runCurrent()
        assertEquals("no new connection within 15 minutes", connectionsBefore, carol.radio.seen.count { it is RadioEvent.Initiated })
        assertFalse(carol.transport.send(1, byteArrayOf(1)))
    }

    @Test
    fun `a friend removed from the list is disconnected`() = runTest {
        alice.knows(7L to bob)
        bob.knows(9L to alice)
        startAll(alice, bob)
        settle()
        assertEquals(setOf(7L), alice.transport.reachable.value)

        // Bob blocks Alice.
        bob.friends.value = emptyList()
        runCurrent()
        assertTrue(bob.transport.reachable.value.isEmpty())
        assertTrue(alice.transport.reachable.value.isEmpty())
        advanceTimeBy(DutyCycle.IDLE.offMillis * 2)
        runCurrent()
        assertTrue("and not linked again", alice.transport.reachable.value.isEmpty())
    }

    @Test
    fun `out of range the link drops, back in range it returns`() = runTest {
        alice.knows(7L to bob)
        bob.knows(9L to alice)
        startAll(alice, bob)
        settle()
        airwaves.setInRange(alice.radio.inner, bob.radio.inner, false)
        runCurrent()
        assertTrue(alice.transport.reachable.value.isEmpty())
        assertFalse(alice.transport.send(7, byteArrayOf(1)))

        airwaves.setInRange(alice.radio.inner, bob.radio.inner, true)
        // Found again in the next discovery burst: within one idle rest.
        advanceTimeBy(DutyCycle.IDLE.offMillis + DutyCycle.IDLE.onMillis)
        runCurrent()
        assertEquals(setOf(7L), alice.transport.reachable.value)
    }

    @Test
    fun `a broken stream drops the link`() = runTest {
        alice.knows(7L to bob)
        bob.knows(9L to alice)
        startAll(alice, bob)
        settle()
        // A frame that doesn't belong to the link's encrypted stream.
        alice.radio.inner.send("bob", byteArrayOf(0x22, 0x02, 0x12, 0x00))
        runCurrent()
        assertTrue(bob.transport.reachable.value.isEmpty())
        assertTrue(alice.transport.reachable.value.isEmpty())
        assertTrue(bob.received.isEmpty())
    }

    @Test
    fun `the advertised name is random and changes every 15 minutes`() = runTest {
        startAll(alice)
        runCurrent()
        val first = alice.radio.inner.advertisedName
        assertEquals(EndpointNames.LENGTH, first?.length)
        assertTrue(first!!.all { it in 'A'..'Z' || it in '2'..'7' })
        advanceTimeBy(EndpointNames.ROTATION_MILLIS - 1)
        runCurrent()
        assertEquals(first, alice.radio.inner.advertisedName)
        advanceTimeBy(1)
        runCurrent()
        assertNotEquals(first, alice.radio.inner.advertisedName)
    }

    @Test
    fun `discovery runs in bursts, faster while a message waits, slowest when saving battery`() = runTest {
        startAll(alice)
        runCurrent()
        fun discovering() = alice.radio.inner.discovering

        assertTrue(discovering())
        advanceTimeBy(DutyCycle.IDLE.onMillis)
        runCurrent()
        assertFalse(discovering())
        advanceTimeBy(DutyCycle.IDLE.offMillis - 1)
        runCurrent()
        assertFalse(discovering())
        advanceTimeBy(1)
        runCurrent()
        assertTrue("the next burst", discovering())

        // Resting while idle, a message starts waiting: the rest is cut short.
        advanceTimeBy(DutyCycle.IDLE.onMillis)
        runCurrent()
        assertFalse(discovering())
        alice.demand.value = DiscoveryDemand(waiting = true)
        runCurrent()
        assertTrue(discovering())
        advanceTimeBy(DutyCycle.WAITING.onMillis + DutyCycle.WAITING.offMillis)
        runCurrent()
        assertTrue("waiting: every minute", discovering())

        alice.demand.value = DiscoveryDemand(waiting = true, saving = true)
        advanceTimeBy(DutyCycle.WAITING.onMillis)
        runCurrent()
        assertFalse(discovering())
        advanceTimeBy(DutyCycle.WAITING.offMillis)
        runCurrent()
        assertTrue("saving: that burst's rest was already set", discovering())
        advanceTimeBy(DutyCycle.SAVING.onMillis)
        runCurrent()
        advanceTimeBy(DutyCycle.SAVING.offMillis - 1)
        runCurrent()
        assertFalse(discovering())
    }

    @Test
    fun `the duty cycle`() {
        assertEquals(DutyCycle.IDLE, DutyCycle.plan(DiscoveryDemand()))
        assertEquals(DutyCycle.WAITING, DutyCycle.plan(DiscoveryDemand(waiting = true)))
        assertEquals(DutyCycle.SAVING, DutyCycle.plan(DiscoveryDemand(waiting = true, saving = true)))
        assertEquals(30_000L, DutyCycle.WAITING.onMillis)
        assertEquals(60_000L, DutyCycle.WAITING.offMillis)
        assertEquals(5 * 60_000L, DutyCycle.IDLE.offMillis)
        assertEquals(10 * 60_000L, DutyCycle.SAVING.offMillis)
    }

    @Test
    fun `names never repeat and never say who you are`() {
        val names = EndpointNames(sodium)
        val many = List(1_000) { names.next() }
        assertEquals(many.size, many.toSet().size)
    }

    @Test
    fun `stopping closes every link`() = runTest {
        alice.knows(7L to bob)
        bob.knows(9L to alice)
        startAll(alice, bob)
        settle()
        alice.transport.stop()
        runCurrent()
        assertTrue(alice.transport.reachable.value.isEmpty())
        assertTrue(bob.transport.reachable.value.isEmpty())
        assertEquals(null, alice.radio.inner.advertisedName)
        assertFalse(alice.radio.inner.discovering)
    }

}
