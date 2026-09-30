package app.tfl.core.model.network

/** How TFL will reach the Tor network (saved now, used from Phase 4). */
enum class BridgeMode {
    /** Connect to Tor directly. */
    DIRECT,

    /** obfs4 pluggable transport, for networks that block Tor. */
    OBFS4,

    /** Snowflake bridges via temporary WebRTC proxies. */
    SNOWFLAKE,
}
