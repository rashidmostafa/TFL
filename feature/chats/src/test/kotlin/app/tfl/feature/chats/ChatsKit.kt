package app.tfl.feature.chats

import android.content.Context
import app.tfl.core.crypto.envelope.EnvelopeCodec
import app.tfl.core.crypto.envelope.OpenResult
import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.database.repository.ConversationRepository
import app.tfl.core.database.repository.MessageRepository
import app.tfl.core.database.repository.OutboxRepository
import app.tfl.core.model.Transport
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.MessageBody
import app.tfl.core.model.message.MessageId
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.transport.Applied
import app.tfl.core.transport.ChatPresence
import app.tfl.core.transport.MessageAlerts
import app.tfl.core.transport.Messenger
import app.tfl.core.transport.NearbyReadiness
import app.tfl.core.transport.OutboxFeed
import app.tfl.core.transport.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneOffset

/**
 * One unlocked phone for chat tests: the real messenger and repositories over [SessionFixture], and
 * friends with real keys, whose messages arrive sealed and signed as the transport would hand them over.
 */
internal class ChatsKit(context: Context) {
    val fixture = SessionFixture(context)
    val clock get() = fixture.clock
    val codec = EnvelopeCodec(fixture.sodium)
    val conversations = ConversationRepository(fixture.database)
    val messages = MessageRepository(fixture.database)
    val outbox = OutboxRepository(fixture.database)
    val messenger = Messenger(
        fixture.database, codec, fixture.keyring, fixture.session, fixture.identities, fixture.contacts,
        conversations, messages, outbox, OutboxFeed(), fixture.clock,
    )
    val transport = FakeTransportStatus()
    val readiness = FakeReadiness()
    val presence = ChatPresence()
    val alerts = RecordingAlerts()
    val chatClock = ChatClock { TimeContext(fixture.clock.wall, ZoneOffset.UTC, is24Hour = true) }

    private val derivation = IdentityKeyDerivation(fixture.sodium)
    private val fingerprints = Fingerprints(fixture.sodium)

    suspend fun unlock() {
        fixture.session.commitOnboarding(fixture.draft(fixture.tools.newSeed(), displayName = "Me"))
    }

    /** A friend's phone: its own keys, and its contact id on this phone. */
    inner class Friend(val id: Long, val keys: TransportKeys, val contact: ContactKeys)

    suspend fun friend(name: String, seed: Int, mutual: Boolean = true): Friend {
        val keys = derivation.derive(ByteArray(32) { seed.toByte() }).use { it.forTransport() }
        val contact = ContactKeys(keys.signPublicKey, keys.kexPublicKey, fingerprints.of(keys.signPublicKey))
        val id = fixture.contacts.recordPairing(name, contact, mutual, nowMillis = clock.wall).id
        return Friend(id, keys, contact)
    }

    /** [friend] sends [body]; it arrives now, over Nearby. */
    suspend fun receive(friend: Friend, body: MessageBody): Applied {
        val me = checkNotNull(fixture.identities.get())
        val sealed = codec.seal(friend.keys, ContactKeys(me.signPublicKey, me.kexPublicKey, me.fingerprintHex), body, clock.wall)
        val myKeys = checkNotNull(fixture.keyring.keys.value)
        val opened = codec.open(sealed.bytes, myKeys, { friend.contact }, clock.wall) as OpenResult.Opened
        return messenger.apply(opened, friend.id, clock.wall, Transport.NEARBY)
    }

    suspend fun receiveText(friend: Friend, text: String, replyTo: MessageId? = null, timer: Int = 0) =
        receive(friend, MessageBody.Text(text, replyTo, timer))

    fun close() = fixture.database.close()
}

class FakeTransportStatus : TransportStatus {
    override val reachable = MutableStateFlow<Set<Long>>(emptySet())
    override val running = MutableStateFlow(false)
}

class FakeReadiness : NearbyReadiness {
    override val permitted = MutableStateFlow(true)
    override val ready = MutableStateFlow(true)
}

class RecordingAlerts : MessageAlerts {
    var cleared = 0
        private set

    override fun newMessage(from: String?) = Unit

    override fun clear() {
        cleared++
    }
}

/** Keys for a friend's key change in tests; nothing is ever sealed to them. */
internal object TestIdentity {
    fun keys(seed: Int): ContactKeys {
        val sign = kotlin.random.Random(seed).nextBytes(32)
        return ContactKeys(sign, kotlin.random.Random(seed + 1_000).nextBytes(32), sign.copyOf(16).joinToString("") { "%02X".format(it) })
    }
}
