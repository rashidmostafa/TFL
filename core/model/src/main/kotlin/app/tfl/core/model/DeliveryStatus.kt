package app.tfl.core.model

/** Progress of an outgoing message, in order. */
enum class DeliveryStatus {
    /** Waiting in the outbox for a route. */
    QUEUED,

    /** Handed to a transport. */
    SENT,

    /** The recipient's device acknowledged it. */
    DELIVERED,

    /** The recipient opened it. TFL sends no read receipts, so only design previews use this. */
    READ,

    /** Not delivered within 30 days: the outbox gave up. */
    FAILED,
}
