package app.tfl.core.crypto.link

import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.proto.LinkFrame
import app.tfl.core.model.proto.LinkMessage
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkHandshakeTest {

    private val sodium = JvmSodium.api
    private val handshakes = LinkHandshakes(sodium)
    private val derivation = IdentityKeyDerivation(sodium)
    private val fingerprints = Fingerprints(sodium)

    private inner class Phone(seed: Int) {
        val keys: TransportKeys = derivation.derive(ByteArray(32) { seed.toByte() }).use { it.forTransport() }
        val contact = ContactKeys(keys.signPublicKey, keys.kexPublicKey, fingerprints.of(keys.signPublicKey))

        /** How another phone lists this one among the friends it may talk to. */
        fun asPeer(id: Long) = LinkPeer(id, contact)
    }

    private val alice = Phone(1)
    private val bob = Phone(2)
    private val carol = Phone(3)
    private val dave = Phone(4)

    /** What happened on each side, and every frame each side sent. */
    private class Run(val initiator: LinkProgress?, val responder: LinkProgress?, val sentByInitiator: List<ByteArray>, val sentByResponder: List<ByteArray>)

    /** Passes frames between the two handshakes until both finish or nothing is left to send. */
    private fun run(
        initiator: LinkHandshake,
        responder: LinkHandshake,
        tamper: (fromInitiator: Boolean, index: Int, frame: ByteArray) -> ByteArray = { _, _, frame -> frame },
    ): Run {
        val sentByI = mutableListOf(initiator.hello)
        val sentByR = mutableListOf(responder.hello)
        var deliveredToR = 0
        var deliveredToI = 0
        var endI: LinkProgress? = null
        var endR: LinkProgress? = null
        fun handle(progress: LinkProgress, sent: MutableList<ByteArray>): LinkProgress? = when (progress) {
            is LinkProgress.Continue -> null.also { sent += progress.send }
            is LinkProgress.Linked -> progress.also { sent += it.send }
            is LinkProgress.Failed -> progress
        }
        while ((endR == null && deliveredToR < sentByI.size) || (endI == null && deliveredToI < sentByR.size)) {
            if (endR == null && deliveredToR < sentByI.size) {
                val frame = tamper(true, deliveredToR, sentByI[deliveredToR]).also { deliveredToR++ }
                endR = handle(responder.receive(frame), sentByR)
            }
            if (endI == null && deliveredToI < sentByR.size) {
                val frame = tamper(false, deliveredToI, sentByR[deliveredToI]).also { deliveredToI++ }
                endI = handle(initiator.receive(frame), sentByI)
            }
        }
        return Run(endI, endR, sentByI, sentByR)
    }

    private fun start(role: LinkRole, phone: Phone, vararg friends: LinkPeer) = handshakes.start(role, phone.keys, friends.toList())

    private fun flip(frame: ByteArray, at: Int = frame.size - 1) = frame.copyOf().also { it[at] = (it[at].toInt() xor 1).toByte() }

    @Test
    fun `two friends link, find out who each other is, and talk privately both ways`() {
        val run = run(start(LinkRole.INITIATOR, alice, bob.asPeer(7)), start(LinkRole.RESPONDER, bob, carol.asPeer(1), alice.asPeer(9)))
        val onAlice = run.initiator as LinkProgress.Linked
        val onBob = run.responder as LinkProgress.Linked
        assertEquals(7, onAlice.peer.id)
        assertEquals(9, onBob.peer.id)

        val envelope = sodium.randomBytes(300)
        val frame = onAlice.channel.sealEnvelope(envelope)
        val received = LinkMessage.parseFrom(checkNotNull(onBob.channel.open(frame)))
        assertArrayEquals(envelope, received.envelope.toByteArray())
        val reply = LinkMessage.parseFrom(checkNotNull(onAlice.channel.open(onBob.channel.sealEnvelope(byteArrayOf(1, 2, 3)))))
        assertArrayEquals(byteArrayOf(1, 2, 3), reply.envelope.toByteArray())
        assertNull("a replayed frame", onBob.channel.open(frame))
    }

    @Test
    fun `a stranger learns nothing and is dropped, whichever side it's on`() {
        // Carol answers a knock she can't match with silence: nothing after her hello.
        val strangerResponds = run(start(LinkRole.INITIATOR, alice, bob.asPeer(1)), start(LinkRole.RESPONDER, carol, dave.asPeer(1)))
        assertEquals(LinkProgress.Failed(LinkFailure.UNKNOWN_PEER), strangerResponds.responder)
        assertEquals(1, strangerResponds.sentByResponder.size)
        assertNull("Alice is left waiting and gives up on her own", strangerResponds.initiator)

        // Carol knocks on Bob, who doesn't know her: Bob says nothing either.
        val strangerKnocks = run(start(LinkRole.INITIATOR, carol, dave.asPeer(1)), start(LinkRole.RESPONDER, bob, alice.asPeer(1)))
        assertEquals(LinkProgress.Failed(LinkFailure.UNKNOWN_PEER), strangerKnocks.responder)
        assertEquals(1, strangerKnocks.sentByResponder.size)
    }

    @Test
    fun `a blocked friend is treated as a stranger`() {
        // Bob blocked Alice, so she isn't among the friends he may talk to.
        val aliceKnocks = run(start(LinkRole.INITIATOR, alice, bob.asPeer(1)), start(LinkRole.RESPONDER, bob, carol.asPeer(1)))
        assertEquals(LinkProgress.Failed(LinkFailure.UNKNOWN_PEER), aliceKnocks.responder)
        val bobKnocks = run(start(LinkRole.INITIATOR, bob, carol.asPeer(1)), start(LinkRole.RESPONDER, alice, bob.asPeer(1)))
        assertEquals(LinkProgress.Failed(LinkFailure.UNKNOWN_PEER), bobKnocks.responder)
    }

    @Test
    fun `a friend whose key changed is a stranger until verified`() {
        // Bob reinstalled TFL: Alice still has his old key, which his new identity can't match.
        val newBob = Phone(22)
        val run = run(start(LinkRole.INITIATOR, alice, bob.asPeer(1)), start(LinkRole.RESPONDER, newBob, alice.asPeer(1)))
        assertEquals(LinkProgress.Failed(LinkFailure.UNKNOWN_PEER), run.responder)
        // With the new key recorded but unverified, Alice leaves him out, which is the blocked case above.
    }

    @Test
    fun `knowing the pair secret isn't enough without the identity key`() {
        // Alice has Bob's key-agreement key right but a different signing key on file.
        val wrongIdentity = LinkPeer(1, ContactKeys(carol.keys.signPublicKey, bob.keys.kexPublicKey, "0".repeat(32)))
        val run = run(start(LinkRole.INITIATOR, alice, wrongIdentity), start(LinkRole.RESPONDER, bob, alice.asPeer(1)))
        assertEquals(LinkProgress.Failed(LinkFailure.BAD_AUTH), run.initiator)
    }

    @Test
    fun `any change to a handshake frame breaks it`() {
        fun linkedBoth(run: Run) = run.initiator is LinkProgress.Linked && run.responder is LinkProgress.Linked
        // Initiator frames: 0 hello, 1 knock, 2 auth. Responder frames: 0 hello, 1 answer, 2 auth.
        for (fromInitiator in listOf(true, false)) {
            for (index in 0..2) {
                val run = run(
                    start(LinkRole.INITIATOR, alice, bob.asPeer(1)),
                    start(LinkRole.RESPONDER, bob, alice.asPeer(1)),
                ) { sender, i, frame -> if (sender == fromInitiator && i == index) flip(frame) else frame }
                assertTrue("frame $index from the ${if (fromInitiator) "initiator" else "responder"}", !linkedBoth(run))
            }
        }
    }

    @Test
    fun `a recorded knock can't be replayed into a new connection`() {
        var recorded: ByteArray? = null
        run(start(LinkRole.INITIATOR, alice, bob.asPeer(1)), start(LinkRole.RESPONDER, bob, alice.asPeer(1))) { fromI, i, frame ->
            if (fromI && i == 1) recorded = frame
            frame
        }
        val replayed = run(start(LinkRole.INITIATOR, alice, bob.asPeer(1)), start(LinkRole.RESPONDER, bob, alice.asPeer(1))) { fromI, i, frame ->
            if (fromI && i == 1) checkNotNull(recorded) else frame
        }
        assertEquals(LinkProgress.Failed(LinkFailure.UNKNOWN_PEER), replayed.responder)
    }

    @Test
    fun `a knock has the same shape whatever the number of friends`() {
        fun knockOf(vararg friends: LinkPeer): LinkFrame {
            val run = run(start(LinkRole.INITIATOR, alice, *friends), start(LinkRole.RESPONDER, dave))
            return LinkFrame.parseFrom(run.sentByInitiator[1])
        }
        val one = knockOf(bob.asPeer(1))
        val three = knockOf(bob.asPeer(1), carol.asPeer(2), dave.asPeer(3))
        assertEquals(LinkHandshakes.KNOCK_TAGS, one.knock.tagsCount)
        assertEquals(one.toByteArray().size, three.toByteArray().size)
        val tags = three.knock.tagsList.map { it.toByteArray().toList() }
        assertEquals(tags.size, tags.toSet().size)
    }

    @Test
    fun `reflected or out-of-order frames are refused`() {
        val handshake = start(LinkRole.INITIATOR, alice, bob.asPeer(1))
        assertEquals(LinkProgress.Failed(LinkFailure.MALFORMED), handshake.receive(handshake.hello))

        val responder = start(LinkRole.RESPONDER, bob, alice.asPeer(1))
        val initiator = start(LinkRole.INITIATOR, alice, bob.asPeer(1))
        initiator.receive(responder.hello)
        assertEquals("a second hello", LinkProgress.Failed(LinkFailure.OUT_OF_ORDER), initiator.receive(responder.hello))
        assertEquals(LinkProgress.Failed(LinkFailure.MALFORMED), start(LinkRole.RESPONDER, bob).receive(byteArrayOf(9, 9, 9)))
    }
}
