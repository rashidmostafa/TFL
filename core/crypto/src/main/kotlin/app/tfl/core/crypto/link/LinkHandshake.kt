package app.tfl.core.crypto.link

import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.proto.LinkFrame
import app.tfl.core.model.proto.LinkMessage
import app.tfl.core.model.proto.answer
import app.tfl.core.model.proto.auth
import app.tfl.core.model.proto.hello
import app.tfl.core.model.proto.knock
import app.tfl.core.model.proto.linkFrame
import app.tfl.core.model.proto.linkMessage
import app.tfl.core.model.proto.sealed
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import javax.inject.Inject
import javax.inject.Singleton

/** A friend this phone may link with: their keys, and whatever id the caller knows them by. */
class LinkPeer(val id: Long, val keys: ContactKeys)

/** The phone that asked to connect is the initiator; the other one, the responder. */
enum class LinkRole { INITIATOR, RESPONDER }

sealed interface LinkProgress {
    /** Send these frames (if any) and wait for the next one. */
    class Continue(val send: List<ByteArray>) : LinkProgress

    /** Both phones proved who they are: from now on everything goes through [channel]. */
    class Linked(val peer: LinkPeer, val channel: LinkChannel, val send: List<ByteArray>) : LinkProgress

    /** Stop and disconnect without another word: nothing more is revealed to this phone. */
    data class Failed(val reason: LinkFailure) : LinkProgress
}

enum class LinkFailure {
    MALFORMED,
    UNSUPPORTED_VERSION,

    /** No friend of this phone's on the other side (a stranger, a blocked friend, an unverified new key). */
    UNKNOWN_PEER,

    /** The other phone knew the pair secret but couldn't sign as the friend's identity key. */
    BAD_AUTH,
    OUT_OF_ORDER,
}

/**
 * Private mutual authentication between two phones, after Nearby connects (see
 * docs/SECURITY_DESIGN.md, "Linking with a friend"):
 *
 * 1. Both send a Hello: a fresh nonce and a fresh X25519 key.
 * 2. The initiator knocks with one tag per friend it may talk to, keyed with the secret it shares
 *    with that friend, padded with random tags to a fixed count and sorted. Tags look random to
 *    anyone who doesn't share that secret, and change every connection.
 * 3. The responder answers only if a tag is from one of its own friends: a stranger learns nothing.
 * 4. Both derive one-way keys from the fresh keys, the pair secret and the transcript; everything
 *    after this is encrypted with libsodium secretstream.
 * 5. Both sign the transcript with their Ed25519 identity key, and check the other's signature.
 */
@Singleton
class LinkHandshakes @Inject constructor(private val sodium: SodiumApi) {

    /** Starts a handshake. [friends] are the friends this phone may talk to right now. */
    fun start(role: LinkRole, me: TransportKeys, friends: List<LinkPeer>): LinkHandshake =
        LinkHandshake(sodium, role, me, friends.take(KNOCK_TAGS))

    companion object {
        /** Friends one knock can name; the rest of the knock is random padding. */
        const val KNOCK_TAGS = 64
        internal const val TAG_BYTES = 16
        internal const val NONCE_BYTES = 32
        internal const val VERSION = 1
    }
}

class LinkHandshake internal constructor(
    private val sodium: SodiumApi,
    private val role: LinkRole,
    private val me: TransportKeys,
    private val friends: List<LinkPeer>,
) : AutoCloseable {

    private enum class State { AWAIT_HELLO, AWAIT_KNOCK, AWAIT_ANSWER, AWAIT_AUTH, DONE }

    private val nonce = sodium.randomBytes(LinkHandshakes.NONCE_BYTES)
    private val ephemeral = sodium.kxKeyPair()
    private var state = State.AWAIT_HELLO
    private var peerEphemeral: ByteArray? = null
    private var transcript = ByteArray(0)
    private var peer: LinkPeer? = null
    private var pairKey: ByteArray? = null
    private var channel: LinkChannel? = null

    /** This phone's first frame; send it as soon as Nearby connects. */
    val hello: ByteArray = linkFrame {
        hello = hello {
            version = LinkHandshakes.VERSION
            nonce = ByteString.copyFrom(this@LinkHandshake.nonce)
            ephemeralKey = ByteString.copyFrom(ephemeral.first)
        }
    }.toByteArray()

    /** Feeds the other phone's next frame. */
    fun receive(bytes: ByteArray): LinkProgress {
        val frame = try {
            LinkFrame.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
            return fail(LinkFailure.MALFORMED)
        }
        return try {
            when (state) {
                State.AWAIT_HELLO -> if (frame.hasHello()) onHello(bytes, frame) else fail(LinkFailure.OUT_OF_ORDER)
                State.AWAIT_KNOCK -> if (frame.hasKnock()) onKnock(bytes, frame) else fail(LinkFailure.OUT_OF_ORDER)
                State.AWAIT_ANSWER -> if (frame.hasAnswer()) onAnswer(bytes, frame) else fail(LinkFailure.OUT_OF_ORDER)
                State.AWAIT_AUTH -> if (frame.hasSealed()) onAuth(bytes) else fail(LinkFailure.OUT_OF_ORDER)
                State.DONE -> fail(LinkFailure.OUT_OF_ORDER)
            }
        } catch (e: CryptoException) {
            fail(LinkFailure.MALFORMED) // e.g. a low-order ephemeral key
        }
    }

    private fun onHello(bytes: ByteArray, frame: LinkFrame): LinkProgress {
        val hello = frame.hello
        if (hello.version != LinkHandshakes.VERSION) return fail(LinkFailure.UNSUPPORTED_VERSION)
        if (hello.nonce.size() != LinkHandshakes.NONCE_BYTES || hello.ephemeralKey.size() != SodiumApi.KEY_BYTES) return fail(LinkFailure.MALFORMED)
        val theirs = hello.ephemeralKey.toByteArray()
        // Our own hello coming back is a reflection, not another phone.
        if (theirs.contentEquals(ephemeral.first) || hello.nonce.toByteArray().contentEquals(nonce)) return fail(LinkFailure.MALFORMED)
        peerEphemeral = theirs
        transcript = if (role == LinkRole.INITIATOR) hash(TRANSCRIPT_CONTEXT, this.hello, bytes) else hash(TRANSCRIPT_CONTEXT, bytes, this.hello)
        return if (role == LinkRole.INITIATOR) {
            val knock = linkFrame {
                knock = knock { tags.addAll(knockTags().map(ByteString::copyFrom)) }
            }.toByteArray()
            transcript = hash(transcript, knock)
            state = State.AWAIT_ANSWER
            LinkProgress.Continue(listOf(knock))
        } else {
            state = State.AWAIT_KNOCK
            LinkProgress.Continue(emptyList())
        }
    }

    /** Responder: is one of these tags from a friend of ours? If not, say nothing. */
    private fun onKnock(bytes: ByteArray, frame: LinkFrame): LinkProgress {
        val tags = frame.knock.tagsList
        if (tags.size != LinkHandshakes.KNOCK_TAGS || tags.any { it.size() != LinkHandshakes.TAG_BYTES }) return fail(LinkFailure.MALFORMED)
        val knockTranscript = transcript
        transcript = hash(transcript, bytes)
        val tagBytes = tags.map { it.toByteArray() }
        val found = friends.firstNotNullOfOrNull { friend ->
            val key = pairKeyWith(friend)
            val expected = sodium.genericHash(LinkHandshakes.TAG_BYTES, KNOCK_CONTEXT + knockTranscript, key)
            if (tagBytes.any { sodium.constantTimeEquals(it, expected) }) friend to key else null.also { sodium.wipe(key) }
        } ?: return fail(LinkFailure.UNKNOWN_PEER)
        peer = found.first
        pairKey = found.second
        val answer = linkFrame {
            answer = answer { tag = ByteString.copyFrom(sodium.genericHash(LinkHandshakes.TAG_BYTES, ANSWER_CONTEXT + transcript, found.second)) }
        }.toByteArray()
        transcript = hash(transcript, answer)
        val channel = openChannel()
        state = State.AWAIT_AUTH
        return LinkProgress.Continue(listOf(answer, channel.seal(authMessage())))
    }

    /** Initiator: which friend answered? */
    private fun onAnswer(bytes: ByteArray, frame: LinkFrame): LinkProgress {
        if (frame.answer.tag.size() != LinkHandshakes.TAG_BYTES) return fail(LinkFailure.MALFORMED)
        val tag = frame.answer.tag.toByteArray()
        val found = friends.firstNotNullOfOrNull { friend ->
            val key = pairKeyWith(friend)
            val expected = sodium.genericHash(LinkHandshakes.TAG_BYTES, ANSWER_CONTEXT + transcript, key)
            if (sodium.constantTimeEquals(tag, expected)) friend to key else null.also { sodium.wipe(key) }
        } ?: return fail(LinkFailure.UNKNOWN_PEER)
        peer = found.first
        pairKey = found.second
        transcript = hash(transcript, bytes)
        val channel = openChannel()
        state = State.AWAIT_AUTH
        return LinkProgress.Continue(listOf(channel.seal(authMessage())))
    }

    /** Both: the other phone's identity signature over the whole handshake. */
    private fun onAuth(bytes: ByteArray): LinkProgress {
        val channel = checkNotNull(channel)
        val friend = checkNotNull(peer)
        val message = channel.open(bytes)?.let {
            try {
                LinkMessage.parseFrom(it)
            } catch (e: InvalidProtocolBufferException) {
                null
            }
        }
        if (message == null || !message.hasAuth()) return fail(LinkFailure.BAD_AUTH)
        val theirRole = if (role == LinkRole.INITIATOR) LinkRole.RESPONDER else LinkRole.INITIATOR
        val signed = AUTH_CONTEXT + byteArrayOf(theirRole.byte) + transcript
        if (!sodium.verifyDetached(message.auth.signature.toByteArray(), signed, friend.keys.signPublicKey)) return fail(LinkFailure.BAD_AUTH)
        state = State.DONE
        this.channel = null // handed over: the caller closes it now
        return LinkProgress.Linked(friend, channel, emptyList())
    }

    private fun authMessage(): ByteArray = linkMessage {
        auth = auth { signature = ByteString.copyFrom(sodium.signDetached(AUTH_CONTEXT + byteArrayOf(role.byte) + transcript, me.signSecretKey)) }
    }.toByteArray()

    /**
     * One-way keys from the fresh keys (crypto_kx: the initiator is the client), mixed with the pair
     * key and the transcript, so only these two friends in this connection can derive them.
     */
    private fun openChannel(): LinkChannel {
        val (rx, tx) = sodium.kxSessionKeys(role == LinkRole.INITIATOR, ephemeral.first, ephemeral.second, checkNotNull(peerEphemeral))
        val key = checkNotNull(pairKey)
        val initiatorToResponder = if (role == LinkRole.INITIATOR) tx else rx
        val responderToInitiator = if (role == LinkRole.INITIATOR) rx else tx
        val i2r = sodium.genericHash(SodiumApi.KEY_BYTES, KEY_CONTEXT_I2R + transcript + initiatorToResponder, key)
        val r2i = sodium.genericHash(SodiumApi.KEY_BYTES, KEY_CONTEXT_R2I + transcript + responderToInitiator, key)
        sodium.wipe(rx, tx)
        val opened = if (role == LinkRole.INITIATOR) {
            LinkChannel(sodium, sendKey = i2r, receiveKey = r2i)
        } else {
            LinkChannel(sodium, sendKey = r2i, receiveKey = i2r)
        }
        channel = opened
        return opened
    }

    /** One tag per friend, the rest random, sorted so the order reveals nothing. */
    private fun knockTags(): List<ByteArray> {
        val tags = friends.map { friend ->
            val key = pairKeyWith(friend)
            sodium.genericHash(LinkHandshakes.TAG_BYTES, KNOCK_CONTEXT + transcript, key).also { sodium.wipe(key) }
        } + List(LinkHandshakes.KNOCK_TAGS - friends.size) { sodium.randomBytes(LinkHandshakes.TAG_BYTES) }
        return tags.sortedWith(UNSIGNED_ORDER)
    }

    /**
     * The secret only this phone and [friend] can compute: crypto_kx on the two identity X25519 keys
     * (the lower public key plays the client), hashed into one key both sides share.
     */
    private fun pairKeyWith(friend: LinkPeer): ByteArray {
        val theirs = friend.keys.kexPublicKey
        val client = UNSIGNED_ORDER.compare(me.kexPublicKey, theirs) < 0
        val (rx, tx) = sodium.kxSessionKeys(client, me.kexPublicKey, me.kexSecretKey, theirs)
        val clientToServer = if (client) tx else rx
        val serverToClient = if (client) rx else tx
        return sodium.genericHash(SodiumApi.KEY_BYTES, PAIR_CONTEXT + clientToServer + serverToClient).also { sodium.wipe(rx, tx) }
    }

    private fun hash(first: ByteArray, vararg parts: ByteArray): ByteArray {
        var joined = first
        for (part in parts) joined += part
        return sodium.genericHash(SodiumApi.KEY_BYTES, joined)
    }

    private fun fail(reason: LinkFailure): LinkProgress {
        state = State.DONE
        close()
        return LinkProgress.Failed(reason)
    }

    /** Wipes the handshake's secrets; a channel already handed out is the caller's to close. */
    override fun close() {
        sodium.wipe(ephemeral.second, pairKey)
        channel?.close()
        channel = null
    }

    private val LinkRole.byte: Byte get() = if (this == LinkRole.INITIATOR) 1 else 2

    private companion object {
        val TRANSCRIPT_CONTEXT = "TFL-link-v1".encodeToByteArray() + 0.toByte()
        val KNOCK_CONTEXT = "TFL-link-knock-v1".encodeToByteArray() + 0.toByte()
        val ANSWER_CONTEXT = "TFL-link-answer-v1".encodeToByteArray() + 0.toByte()
        val AUTH_CONTEXT = "TFL-link-auth-v1".encodeToByteArray() + 0.toByte()
        val PAIR_CONTEXT = "TFL-pair-key-v1".encodeToByteArray() + 0.toByte()
        val KEY_CONTEXT_I2R = "TFL-link-key-v1".encodeToByteArray() + 0.toByte() + 1.toByte()
        val KEY_CONTEXT_R2I = "TFL-link-key-v1".encodeToByteArray() + 0.toByte() + 2.toByte()
        val UNSIGNED_ORDER = Comparator<ByteArray> { a, b ->
            for (i in 0 until minOf(a.size, b.size)) {
                val difference = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
                if (difference != 0) return@Comparator difference
            }
            a.size - b.size
        }
    }
}

/**
 * The encrypted link after the handshake: libsodium secretstream in each direction, so every frame
 * is authenticated and a changed, dropped, replayed or reordered frame is refused.
 */
class LinkChannel internal constructor(
    private val sodium: SodiumApi,
    private val sendKey: ByteArray,
    private val receiveKey: ByteArray,
) : AutoCloseable {
    private val push = sodium.streamPush(sendKey)
    private var pull: SodiumApi.StreamPull? = null
    private var headerSent = false
    private var broken = false

    /** A frame carrying [message] (a serialized LinkMessage). */
    fun seal(message: ByteArray): ByteArray {
        val ciphertext = push.push(message)
        val first = !headerSent
        headerSent = true
        return linkFrame {
            sealed = sealed {
                if (first) header = ByteString.copyFrom(push.header)
                this.ciphertext = ByteString.copyFrom(ciphertext)
            }
        }.toByteArray()
    }

    /** The message in [frame], or null if it isn't the next valid frame; the channel is then unusable. */
    fun open(frame: ByteArray): ByteArray? {
        if (broken) return null
        val sealed = try {
            LinkFrame.parseFrom(frame).takeIf { it.hasSealed() }?.sealed
        } catch (e: InvalidProtocolBufferException) {
            null
        }
        // The stream header comes with the first frame, and only with it.
        val stream = when {
            sealed == null -> null
            pull == null && !sealed.header.isEmpty -> sodium.streamPull(receiveKey, sealed.header.toByteArray())?.also { pull = it }
            pull != null && sealed.header.isEmpty -> pull
            else -> null
        }
        val message = if (sealed != null) stream?.pull(sealed.ciphertext.toByteArray()) else null
        if (message == null) broken = true
        return message
    }

    /** Wraps an envelope for this channel. */
    fun sealEnvelope(envelope: ByteArray): ByteArray = seal(linkMessage { this.envelope = ByteString.copyFrom(envelope) }.toByteArray())

    override fun close() {
        push.close()
        pull?.close()
        sodium.wipe(sendKey, receiveKey)
    }
}
