package app.tfl.core.model

/**
 * How an envelope travels to a peer. Entries are declared in routing-priority order:
 * a direct Nearby link beats a mesh relay, which beats Tor; [QUEUED] means no route yet.
 */
enum class Transport {
    /** Direct Bluetooth / Wi-Fi Direct link through Nearby Connections. */
    NEARBY,

    /** Relayed hop by hop through other TFL phones. */
    MESH,

    /** Between onion services over Tor. */
    TOR,

    /** No route right now; held in the outbox and retried. */
    QUEUED,
    ;

    companion object {
        /** The highest-priority transport in [reachable], or [QUEUED] when nothing is reachable. */
        fun preferred(reachable: Collection<Transport>): Transport =
            entries.firstOrNull { it != QUEUED && it in reachable } ?: QUEUED
    }
}
