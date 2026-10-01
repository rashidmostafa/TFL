package app.tfl.core.transport.nearby

import app.tfl.core.crypto.sodium.SodiumApi
import javax.inject.Inject

/** What discovery should do: whether something waits for a friend out of reach, and whether to save battery. */
data class DiscoveryDemand(val waiting: Boolean = false, val saving: Boolean = false)

/**
 * Discovery runs in bursts; advertising runs all the time, so friends looking for this phone still
 * find it between bursts. Bursts come faster while a message waits for a friend who isn't linked,
 * and much slower when saving battery (TFL's battery saver, the system's, or a low battery).
 */
object DutyCycle {
    data class Plan(val onMillis: Long, val offMillis: Long)

    val WAITING = Plan(onMillis = 30_000, offMillis = 60_000)
    val IDLE = Plan(onMillis = 30_000, offMillis = 5 * 60_000)
    val SAVING = Plan(onMillis = 20_000, offMillis = 10 * 60_000)

    fun plan(demand: DiscoveryDemand): Plan = when {
        demand.saving -> SAVING
        demand.waiting -> WAITING
        else -> IDLE
    }
}

/**
 * The names this phone advertises under: 8 random characters, new every 15 minutes. Never the
 * display name or anything derived from a key, so a scanner can't recognise the phone later.
 */
class EndpointNames @Inject constructor(private val sodium: SodiumApi) {
    fun next(): String = sodium.randomBytes(LENGTH).joinToString("") { ALPHABET[it.toInt() and 31].toString() }

    companion object {
        const val LENGTH = 8
        const val ROTATION_MILLIS = 15 * 60_000L
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    }
}
