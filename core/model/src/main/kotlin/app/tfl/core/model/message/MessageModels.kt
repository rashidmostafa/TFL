package app.tfl.core.model.message

/** 16 bytes compared by value (a ByteArray compares by identity), shown as hex. */
abstract class Bytes16 protected constructor(bytes: ByteArray) {
    private val value: ByteArray = bytes.copyOf()

    init {
        require(value.size == SIZE) { "Expected $SIZE bytes, got ${value.size}" }
    }

    val bytes: ByteArray get() = value.copyOf()

    val hex: String get() = value.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean =
        other != null && other::class == this::class && value.contentEquals((other as Bytes16).value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "${this::class.simpleName}(${hex.take(8)}…)"

    companion object {
        const val SIZE = 16
    }
}

/** A message's random id: the same on every phone and every transport, used to spot duplicates. */
class MessageId(bytes: ByteArray) : Bytes16(bytes) {
    companion object {
        const val SIZE = Bytes16.SIZE
    }
}

/** BLAKE2b-128 of an Ed25519 identity key: names a sender or recipient without the whole key. */
class KeyId(bytes: ByteArray) : Bytes16(bytes) {
    companion object {
        const val SIZE = Bytes16.SIZE
    }
}

/** What an envelope carries, once opened. */
sealed interface MessageBody {
    data class Text(val text: String, val replyTo: MessageId? = null, val expiresAfterSeconds: Int = 0) : MessageBody

    /** These messages reached the recipient's phone. */
    data class Receipt(val delivered: List<MessageId>) : MessageBody

    /** [emoji] null removes the sender's reaction. */
    data class Reaction(val target: MessageId, val emoji: String?) : MessageBody

    data class Edit(val target: MessageId, val text: String, val editNumber: Int) : MessageBody

    data class Delete(val target: MessageId) : MessageBody

    /** The conversation's disappearing timer; 0 turns it off. */
    data class Timer(val expiresAfterSeconds: Int) : MessageBody
}

/** Limits both phones enforce: messages outside them are refused. */
object MessageLimits {
    const val MAX_TEXT_CHARS = 4_000
    const val MAX_EMOJI_CHARS = 16
    const val MAX_RECEIPT_IDS = 256
    const val EDIT_WINDOW_MILLIS = 15 * 60_000L

    /** Disappearing timers: off, 5 minutes, 1 hour, 1 day, 1 week. */
    val TIMER_SECONDS = listOf(0, 5 * 60, 60 * 60, 24 * 60 * 60, 7 * 24 * 60 * 60)

    /** Anything older can't be checked against the 30 days of remembered ids, so it's refused. */
    const val MAX_AGE_MILLIS = 30L * 24 * 60 * 60_000

    /** How far ahead of this phone's clock a message may claim to be. */
    const val MAX_CLOCK_AHEAD_MILLIS = 10 * 60_000L
}
