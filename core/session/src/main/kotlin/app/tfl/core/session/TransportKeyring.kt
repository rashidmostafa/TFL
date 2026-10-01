package app.tfl.core.session

import app.tfl.core.crypto.identity.TransportKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The keys the transport links and seals with, in memory only. [AppSession] hands over the unlocked
 * profile's keys at every unlock; they're wiped when TFL locks, unless the transport's background
 * service is running ([keepWhileLocked]), so friends' messages keep moving while locked. They're
 * also wiped when the service stops, when TFL is wiped, and when another profile unlocks: after the
 * duress PIN opens the decoy, only the decoy's keys are here.
 */
@Singleton
class TransportKeyring @Inject constructor() {

    private val current = MutableStateFlow<TransportKeys?>(null)

    /** The keys to use, or null: the transport stops and forgets what it held in memory. */
    val keys: StateFlow<TransportKeys?> = current.asStateFlow()

    /** Set by the transport while its background service runs. */
    @Volatile
    var keepWhileLocked: Boolean = false

    /**
     * Holds the unlocked profile's [keys]. The same identity unlocking again keeps what's held (the
     * new copy is wiped), so links stay up; another identity replaces it and the old keys are wiped.
     */
    @Synchronized
    internal fun hold(keys: TransportKeys) {
        val held = current.value
        if (held != null && held.signPublicKey.contentEquals(keys.signPublicKey)) {
            keys.close()
            return
        }
        // Observers move to the new keys (or stop) before the old ones are wiped.
        current.value = keys
        held?.close()
    }

    /** TFL locked. */
    @Synchronized
    internal fun locked() {
        if (!keepWhileLocked) release()
    }

    /** Wipes the keys: the service stopped, or TFL is being wiped. */
    @Synchronized
    fun release() {
        val held = current.value ?: return
        current.value = null
        held.close()
    }
}
