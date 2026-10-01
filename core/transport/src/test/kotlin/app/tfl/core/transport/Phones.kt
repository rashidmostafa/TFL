package app.tfl.core.transport

import android.content.Context
import app.tfl.core.crypto.envelope.EnvelopeCodec
import app.tfl.core.crypto.inbox.LockedInboxStore
import app.tfl.core.crypto.link.LinkHandshakes
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.database.repository.OutboxRepository
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.ChatMessage
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.UnlockOutcome
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.transport.nearby.EndpointNames
import app.tfl.core.transport.radio.Airwaves
import app.tfl.core.transport.radio.FaultyRadio
import app.tfl.core.transport.radio.InMemoryRadio
import app.tfl.core.transport.radio.RadioFaults
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import java.io.File

/**
 * A whole phone for transport tests: its own vault, database, keyring, messenger and transport,
 * on a radio in [airwaves], all on the test's virtual time. Phones start in range of each other.
 */
class TestPhone(
    context: Context,
    val name: String,
    private val airwaves: Airwaves,
    private val test: TestScope,
    ownRadio: InMemoryRadio? = null,
) {
    val fixture = SessionFixture(context, phone = name)
    val clock = SchedulerClock(test.testScheduler)
    val radio = ownRadio ?: airwaves.radio(name)
    val faults = RadioFaults()
    val alerts = RecordingAlerts()
    val wakeup = RecordingWakeup()
    val readiness = FakeReadiness()
    val power = FakePowerSaving()
    val service = FakeService()
    val presence = ChatPresence()
    val codec = EnvelopeCodec(fixture.sodium)
    val conversations = ConversationRepository(fixture.database)
    val messages = MessageRepository(fixture.database)
    val outbox = OutboxRepository(fixture.database)
    val inbox = LockedInboxStore(File(context.noBackupFilesDir, "inbox-$name").apply { mkdirs() }, fixture.keys)
    val feed = OutboxFeed()
    val messenger = Messenger(
        fixture.database, codec, fixture.keyring, fixture.session, fixture.identities, fixture.contacts,
        conversations, messages, outbox, feed, clock,
    )
    val controller: TransportController

    /** The transport's events, printed with the virtual time: set TFL_TRANSPORT_LOG=1 to see them. */
    private val log = if (System.getenv("TFL_TRANSPORT_LOG") == null) {
        TransportLog.NONE
    } else {
        TransportLog { message -> println("${test.testScheduler.currentTime} [$name] ${message()}") }
    }

    init {
        val dispatcher = StandardTestDispatcher(test.testScheduler)
        val deps = EngineDeps(
            fixture.database, codec, LinkHandshakes(fixture.sodium), EndpointNames(fixture.sodium), messenger,
            fixture.identities, fixture.contacts, outbox, feed, fixture.settings, inbox, clock, alerts, wakeup, presence, log, dispatcher,
        )
        controller = TransportController(
            fixture.keyring, fixture.session, fixture.database, fixture.settings, messages, messenger, deps,
            FaultyRadio(radio, faults), readiness, power, service, clock, test.backgroundScope, dispatcher,
        )
    }

    suspend fun onboard(decoy: Boolean = false) {
        fixture.session.commitOnboarding(
            fixture.draft(
                fixture.tools.newSeed(),
                displayName = name,
                duressPin = if (decoy) SessionFixture.DURESS_PIN else null,
                duressMode = if (decoy) DuressMode.DECOY else DuressMode.NONE,
            ),
        )
        controller.start()
    }

    /** This phone's identity, as a friend's pairing scan records it. */
    suspend fun identityKeys(): ContactKeys = checkNotNull(fixture.identities.get()).let { ContactKeys(it.signPublicKey, it.kexPublicKey, it.fingerprintHex) }

    /** Both phones scanned each other in person. Returns [other]'s contact id on this phone. */
    suspend fun befriend(other: TestPhone): Long = fixture.contacts.recordPairing(other.name, other.identityKeys(), mutual = true, nowMillis = clock.currentTimeMillis()).id

    suspend fun contactIdOf(other: TestPhone): Long = checkNotNull(fixture.contacts.findByKey(other.identityKeys().signPublicKey)).id

    suspend fun lock() = fixture.session.lock()

    suspend fun unlock(pin: String = SessionFixture.PIN) = assertEquals(UnlockOutcome.Unlocked, fixture.session.unlock(pin.encodeToByteArray()))

    /** The conversation with [contactId] as shown now. */
    suspend fun thread(contactId: Long): List<ChatMessage> {
        val conversation = conversations.forContact(contactId) ?: return emptyList()
        return messages.messages(conversation.id, clock.currentTimeMillis()).first()
    }

    fun close() = fixture.database.close()
}
