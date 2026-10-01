package app.tfl.core.transport

/**
 * What the transport did, for the debug build's transport log: links up and down, handshake
 * results, frame sizes, retries. Events only: never message content, names or keys. Release
 * builds get a log that drops everything, and the lambdas are never called.
 */
fun interface TransportLog {
    fun event(message: () -> String)

    companion object {
        val NONE = TransportLog { }
    }
}
