package app.tfl.core.crypto.envelope

import app.tfl.core.crypto.identity.TransportKeys
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.model.contact.ContactKeys
import app.tfl.core.model.message.KeyId
import app.tfl.core.model.message.MessageBody
import app.tfl.core.model.message.MessageId
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.model.proto.DeleteBody
import app.tfl.core.model.proto.EditBody
import app.tfl.core.model.proto.Envelope
import app.tfl.core.model.proto.EnvelopeType
import app.tfl.core.model.proto.Inner
import app.tfl.core.model.proto.ReactionBody
import app.tfl.core.model.proto.ReceiptBody
import app.tfl.core.model.proto.SignedInner
import app.tfl.core.model.proto.TextBody
import app.tfl.core.model.proto.TimerBody
import app.tfl.core.model.proto.envelope
import app.tfl.core.model.proto.inner
import app.tfl.core.model.proto.signedInner
import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import javax.inject.Inject
import javax.inject.Singleton

/** An envelope ready for any transport: the bytes, plus what the sender's own phone keeps about it. */
class SealedEnvelope(val msgId: MessageId, val createdAtMillis: Long, val bytes: ByteArray)

sealed interface OpenResult {
    /** A message from [sender], checked end to end. Whether it was seen before is the caller's check. */
    class Opened(val msgId: MessageId, val createdAtMillis: Long, val sender: ContactKeys, val body: MessageBody) : OpenResult

    data class Refused(val reason: Refusal) : OpenResult
}

/** Why an envelope was dropped. Nothing about a refused envelope is ever stored or answered. */
enum class Refusal {
    UNREADABLE,
    UNSUPPORTED_VERSION,

    /** Sealed to someone else (the usual case for a relay), or changed on the way. */
    NOT_FOR_ME,

    /** Sealed to this phone, but signed for someone else: a friend's message re-sealed by another. */
    WRONG_RECIPIENT,

    /** Not from a friend this phone may talk to (unknown, blocked, or an unverified new key). */
    UNKNOWN_SENDER,
    BAD_SIGNATURE,

    /** The signed id or time differs from the envelope's. */
    MISMATCH,

    /** Older than the 30 days of remembered ids. */
    TOO_OLD,

    /** More than 10 minutes ahead of this phone's clock. */
    FROM_THE_FUTURE,

    /** Signed and sealed, but its content breaks the limits (see MessageLimits). */
    INVALID_CONTENT,
}

/**
 * Seals and opens envelopes (see docs/SECURITY_DESIGN.md, "The envelope"). The sender signs the
 * inner message, which names both sender and recipient, pads it to a size bucket and seals it to
 * the recipient's X25519 key. Only the recipient can open it, and only a signature by a known
 * friend is accepted. There is no forward secrecy in version 1.
 */
@Singleton
class EnvelopeCodec @Inject constructor(private val sodium: SodiumApi) {

    fun newMessageId(): MessageId = MessageId(sodium.randomBytes(MessageId.SIZE))

    /** BLAKE2b-128 of an Ed25519 identity key. */
    fun keyId(signPublicKey: ByteArray): KeyId = KeyId(sodium.genericHash(KeyId.SIZE, KEY_ID_CONTEXT + signPublicKey))

    /** Signs [body] as [me], for [to] only, and seals it to their key. */
    fun seal(
        me: TransportKeys,
        to: ContactKeys,
        body: MessageBody,
        createdAtMillis: Long,
        msgId: MessageId = newMessageId(),
    ): SealedEnvelope {
        val inner = inner {
            version = VERSION
            senderKeyId = ByteString.copyFrom(keyId(me.signPublicKey).bytes)
            recipientKeyId = ByteString.copyFrom(keyId(to.signPublicKey).bytes)
            this.msgId = ByteString.copyFrom(msgId.bytes)
            this.createdAtMillis = createdAtMillis
            setBody(body)
        }.toByteArray()
        val signed = signedInner {
            this.inner = ByteString.copyFrom(inner)
            signature = ByteString.copyFrom(sodium.signDetached(SIGNATURE_CONTEXT + inner, me.signSecretKey))
        }.toByteArray()
        val bucket = checkNotNull(BUCKETS.firstOrNull { signed.size < it }) { "Message too large" }
        val padded = sodium.pad(signed, bucket)
        val sealed = sodium.boxSeal(padded, to.kexPublicKey)
        sodium.wipe(padded, signed, inner)
        val envelope = envelope {
            version = VERSION
            this.msgId = ByteString.copyFrom(msgId.bytes)
            this.createdAtMillis = createdAtMillis
            ttlSeconds = TTL_SECONDS
            hopCount = 0
            type = EnvelopeType.ENVELOPE_TYPE_SEALED
            recipientHint = ByteString.copyFrom(hint(to.signPublicKey))
            payload = ByteString.copyFrom(sealed)
        }
        return SealedEnvelope(msgId, createdAtMillis, envelope.toByteArray())
    }

    /**
     * Opens an envelope sent to [me], in this order: structure, hint, unsealing, padding, recipient,
     * sender ([senderFor] returns only friends this phone may talk to), signature, the signed id and
     * time, the clock window, and the content's limits.
     */
    fun open(bytes: ByteArray, me: TransportKeys, senderFor: (KeyId) -> ContactKeys?, nowMillis: Long): OpenResult {
        if (bytes.size > MAX_ENVELOPE_BYTES) return refused(Refusal.UNREADABLE)
        val envelope = try {
            Envelope.parseFrom(bytes)
        } catch (e: InvalidProtocolBufferException) {
            return refused(Refusal.UNREADABLE)
        }
        if (envelope.version != VERSION) return refused(Refusal.UNSUPPORTED_VERSION)
        if (envelope.type != EnvelopeType.ENVELOPE_TYPE_SEALED || envelope.msgId.size() != MessageId.SIZE ||
            envelope.recipientHint.size() != HINT_BYTES || envelope.payload.size() - SodiumApi.BOX_SEAL_BYTES !in BUCKETS
        ) {
            return refused(Refusal.UNREADABLE)
        }
        if (!envelope.recipientHint.toByteArray().contentEquals(hint(me.signPublicKey))) return refused(Refusal.NOT_FOR_ME)

        val padded = sodium.boxSealOpen(envelope.payload.toByteArray(), me.kexPublicKey, me.kexSecretKey)
            ?: return refused(Refusal.NOT_FOR_ME)
        val signedBytes = sodium.unpad(padded, envelope.payload.size() - SodiumApi.BOX_SEAL_BYTES)
        sodium.wipe(padded)
        signedBytes ?: return refused(Refusal.UNREADABLE)
        try {
            val signed = SignedInner.parseFrom(signedBytes)
            val innerBytes = signed.inner.toByteArray()
            val inner = Inner.parseFrom(innerBytes)
            if (inner.version != VERSION) return refused(Refusal.UNSUPPORTED_VERSION)
            if (inner.senderKeyId.size() != KeyId.SIZE || inner.recipientKeyId.size() != KeyId.SIZE) return refused(Refusal.UNREADABLE)
            if (!sodium.constantTimeEquals(inner.recipientKeyId.toByteArray(), keyId(me.signPublicKey).bytes)) {
                return refused(Refusal.WRONG_RECIPIENT)
            }
            val sender = senderFor(KeyId(inner.senderKeyId.toByteArray())) ?: return refused(Refusal.UNKNOWN_SENDER)
            if (!sodium.verifyDetached(signed.signature.toByteArray(), SIGNATURE_CONTEXT + innerBytes, sender.signPublicKey)) {
                return refused(Refusal.BAD_SIGNATURE)
            }
            if (inner.msgId != envelope.msgId || inner.createdAtMillis != envelope.createdAtMillis) return refused(Refusal.MISMATCH)
            val age = nowMillis - inner.createdAtMillis
            if (age > MessageLimits.MAX_AGE_MILLIS) return refused(Refusal.TOO_OLD)
            if (-age > MessageLimits.MAX_CLOCK_AHEAD_MILLIS) return refused(Refusal.FROM_THE_FUTURE)
            val body = inner.body() ?: return refused(Refusal.INVALID_CONTENT)
            return OpenResult.Opened(MessageId(inner.msgId.toByteArray()), inner.createdAtMillis, sender, body)
        } catch (e: InvalidProtocolBufferException) {
            return refused(Refusal.UNREADABLE)
        } finally {
            sodium.wipe(signedBytes)
        }
    }

    internal fun hint(signPublicKey: ByteArray): ByteArray = sodium.genericHash(16, HINT_CONTEXT + signPublicKey).copyOf(HINT_BYTES)

    private fun refused(reason: Refusal) = OpenResult.Refused(reason)

    private fun app.tfl.core.model.proto.InnerKt.Dsl.setBody(body: MessageBody) {
        when (body) {
            is MessageBody.Text -> {
                require(body.text.length <= MessageLimits.MAX_TEXT_CHARS) { "Text too long" }
                text = TextBody.newBuilder()
                    .setText(body.text)
                    .setReplyTo(body.replyTo.toByteString())
                    .setExpiresAfterSeconds(body.expiresAfterSeconds)
                    .build()
            }
            is MessageBody.Receipt -> receipt = ReceiptBody.newBuilder().addAllMsgIds(body.delivered.map { it.toByteString() }).build()
            is MessageBody.Reaction -> reaction = ReactionBody.newBuilder()
                .setTarget(body.target.toByteString())
                .setEmoji(body.emoji.orEmpty())
                .build()
            is MessageBody.Edit -> edit = EditBody.newBuilder()
                .setTarget(body.target.toByteString())
                .setText(body.text)
                .setEditNumber(body.editNumber)
                .build()
            is MessageBody.Delete -> delete = DeleteBody.newBuilder().setTarget(body.target.toByteString()).build()
            is MessageBody.Timer -> timer = TimerBody.newBuilder().setExpiresAfterSeconds(body.expiresAfterSeconds).build()
        }
    }

    /** The body, or null when it breaks the limits every phone enforces. */
    private fun Inner.body(): MessageBody? = when (bodyCase) {
        Inner.BodyCase.TEXT -> text.takeIf {
            it.text.isNotEmpty() && it.text.length <= MessageLimits.MAX_TEXT_CHARS && it.expiresAfterSeconds in MessageLimits.TIMER_SECONDS &&
                (it.replyTo.isEmpty || it.replyTo.size() == MessageId.SIZE)
        }?.let { MessageBody.Text(it.text, it.replyTo.toMessageIdOrNull(), it.expiresAfterSeconds) }
        Inner.BodyCase.RECEIPT -> receipt.takeIf { r ->
            r.msgIdsCount in 1..MessageLimits.MAX_RECEIPT_IDS && r.msgIdsList.all { it.size() == MessageId.SIZE }
        }?.let { r -> MessageBody.Receipt(r.msgIdsList.map { MessageId(it.toByteArray()) }) }
        Inner.BodyCase.REACTION -> reaction.takeIf { it.target.size() == MessageId.SIZE && it.emoji.length <= MessageLimits.MAX_EMOJI_CHARS }
            ?.let { MessageBody.Reaction(MessageId(it.target.toByteArray()), it.emoji.ifEmpty { null }) }
        Inner.BodyCase.EDIT -> edit.takeIf {
            it.target.size() == MessageId.SIZE && it.text.isNotEmpty() && it.text.length <= MessageLimits.MAX_TEXT_CHARS && it.editNumber > 0
        }?.let { MessageBody.Edit(MessageId(it.target.toByteArray()), it.text, it.editNumber) }
        Inner.BodyCase.DELETE -> delete.takeIf { it.target.size() == MessageId.SIZE }?.let { MessageBody.Delete(MessageId(it.target.toByteArray())) }
        Inner.BodyCase.TIMER -> timer.takeIf { it.expiresAfterSeconds in MessageLimits.TIMER_SECONDS }
            ?.let { MessageBody.Timer(it.expiresAfterSeconds) }
        else -> null
    }

    private fun MessageId?.toByteString(): ByteString = if (this == null) ByteString.EMPTY else ByteString.copyFrom(bytes)

    private fun ByteString.toMessageIdOrNull(): MessageId? = if (isEmpty) null else MessageId(toByteArray())

    companion object {
        const val VERSION = 1

        /** Padding buckets: a message's length only shows as one of these. */
        val BUCKETS = listOf(256, 1024, 4096, 16_384, 65_536)
        const val TTL_SECONDS = 30 * 24 * 60 * 60
        const val HINT_BYTES = 2
        private const val MAX_ENVELOPE_BYTES = 65_536 + 1_024
        private val SIGNATURE_CONTEXT = "TFL-envelope-v1".encodeToByteArray() + 0.toByte()
        private val KEY_ID_CONTEXT = "TFL-key-id-v1".encodeToByteArray() + 0.toByte()
        private val HINT_CONTEXT = "TFL-recipient-hint-v1".encodeToByteArray() + 0.toByte()
    }
}
