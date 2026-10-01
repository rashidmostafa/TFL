package app.tfl.core.transport.radio

import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Faults to put in the radio's way, to exercise retries (the debug build's Developer screen sets
 * them; release builds never do): the next [dropNext] payloads are reported sent but never arrive,
 * like frames lost on a link that then breaks, and each payload is held back [delayMillis] first.
 */
@Singleton
class RadioFaults @Inject constructor() {
    @Volatile
    var dropNext: Int = 0

    @Volatile
    var delayMillis: Long = 0

    @Synchronized
    internal fun takeDrop(): Boolean {
        if (dropNext <= 0) return false
        dropNext -= 1
        return true
    }
}

/** [inner] with [faults] applied to what it sends. */
class FaultyRadio(private val inner: Radio, private val faults: RadioFaults) : Radio by inner {
    override suspend fun send(endpointId: String, bytes: ByteArray): Boolean {
        faults.delayMillis.takeIf { it > 0 }?.let { delay(it) }
        if (faults.takeDrop()) return true
        return inner.send(endpointId, bytes)
    }
}
