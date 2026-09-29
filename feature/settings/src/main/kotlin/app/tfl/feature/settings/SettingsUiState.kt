package app.tfl.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class SettingsUiState(
    val displayName: String,
    /** Public key fingerprint in 4-character groups. */
    val fingerprint: List<String>,
    val meshRelayOn: Boolean,
    val torConnected: Boolean,
    val vaultUsage: String,
)

enum class PanicTrigger { SHAKE, VOLUME_DOWN }

enum class SecurityToggle { APP_LOCK, FACE_DOWN_LOCK, CALCULATOR_DISGUISE, CLEAR_KEYS_ON_LOCK }

/** Placeholder switches: held in memory only, not saved and not enforced in Phase 0. */
@Immutable
data class SecuritySettingsUiState(
    val toggles: Map<SecurityToggle, Boolean>,
    val panicTrigger: PanicTrigger,
    /** Protections actually enforced by this build (only screenshot blocking in Phase 0). */
    val enforcedProtections: Int,
    val totalProtections: Int,
) {
    fun isOn(toggle: SecurityToggle): Boolean = toggles[toggle] == true
}

enum class NetworkToggle { TOR, NEARBY, RELAY, BATTERY_SAVER, PAUSE_RELAY_ON_LOW_BATTERY }

/** Placeholder switches and telemetry: held in memory only, not saved and not enforced in Phase 0. */
@Immutable
data class NetworkSettingsUiState(
    val toggles: Map<NetworkToggle, Boolean>,
    val peersNearby: Int,
    val txRate: String,
    val rxRate: String,
    val roundTrip: String,
    /** Recent link activity, 0..1 per bar. */
    val linkActivity: List<Float>,
    val relayUsedMb: Int,
    val relayQuotaMb: Int,
    val relayResetsIn: String,
) {
    fun isOn(toggle: NetworkToggle): Boolean = toggles[toggle] == true
}
