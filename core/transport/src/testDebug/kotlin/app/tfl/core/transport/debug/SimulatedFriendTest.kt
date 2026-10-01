package app.tfl.core.transport.debug

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.link.LinkHandshakes
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.transport.SendOutcome
import app.tfl.core.transport.TestPhone
import app.tfl.core.transport.nearby.EndpointNames
import app.tfl.core.transport.radio.Airwaves
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The debug build's simulated friend: the real transport end to end on one phone. */
@RunWith(AndroidJUnit4::class)
class SimulatedFriendTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val air = SimulatedAir()
    private var phone: TestPhone? = null

    @After
    fun tearDown() {
        phone?.close()
    }

    @Test
    fun `the test friend links, acknowledges and echoes`() = runTest {
        val me = TestPhone(context, "me", Airwaves(), this, ownRadio = air.phone).also { phone = it }
        me.onboard()
        val sodium = me.fixture.sodium
        val seed = sodium.randomBytes(32)
        val derivation = IdentityKeyDerivation(sodium)
        val friendKeys = derivation.derive(seed).use { ContactKeys(it.signPublicKey, it.kexPublicKey, Fingerprints(sodium).of(it.signPublicKey)) }
        val testFriend = me.fixture.contacts.recordPairing("Test friend", friendKeys, mutual = false, nowMillis = me.clock.currentTimeMillis()).id

        val simulated = SimulatedFriend(
            air, derivation, me.codec, LinkHandshakes(sodium), EndpointNames(sodium), me.clock, TransportEvents(me.clock),
            backgroundScope, StandardTestDispatcher(testScheduler),
        )
        simulated.start(seed, me.identityKeys())
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(setOf(testFriend), me.controller.reachable.value)

        val sent = checkNotNull((me.messenger.sendText(testFriend, "hi") as SendOutcome.Queued).message)
        advanceTimeBy(3_000)
        runCurrent()
        val thread = me.thread(testFriend)
        assertEquals(listOf("hi", "Echo: hi"), thread.map { it.text })
        assertEquals(sent.msgId, thread.last().replyTo)
        assertEquals(DeliveryStatus.DELIVERED, me.messages.byMsgId(sent.msgId)?.status)

        simulated.stop()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(me.controller.reachable.value.isEmpty())
    }
}
