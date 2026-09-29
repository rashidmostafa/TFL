package app.tfl.feature.settings.fake

import app.tfl.feature.settings.NetworkSettingsUiState
import app.tfl.feature.settings.NetworkToggle
import app.tfl.feature.settings.PanicTrigger
import app.tfl.feature.settings.SecuritySettingsUiState
import app.tfl.feature.settings.SecurityToggle
import app.tfl.feature.settings.SettingsUiState

/**
 * PHASE 0 PLACEHOLDER DATA adapted from the Stitch mock. The identity and fingerprint are made up;
 * real ones are generated on-device in Phase 1. Security and network settings are wired up in the
 * phases that build them.
 */
internal object FakeSettingsData {
    val settings = SettingsUiState(
        displayName = "Valkyrie-7",
        fingerprint = listOf("9F8A", "31C2", "77D0", "4B01"),
        meshRelayOn = true,
        torConnected = true,
        vaultUsage = "24.8 GB of 128 GB used",
    )

    val security = SecuritySettingsUiState(
        toggles = mapOf(
            SecurityToggle.APP_LOCK to true,
            SecurityToggle.FACE_DOWN_LOCK to true,
            SecurityToggle.CALCULATOR_DISGUISE to false,
            SecurityToggle.CLEAR_KEYS_ON_LOCK to true,
        ),
        panicTrigger = PanicTrigger.SHAKE,
        enforcedProtections = 1,
        totalProtections = 6,
    )

    val network = NetworkSettingsUiState(
        toggles = mapOf(
            NetworkToggle.TOR to true,
            NetworkToggle.NEARBY to true,
            NetworkToggle.RELAY to true,
            NetworkToggle.BATTERY_SAVER to false,
            NetworkToggle.PAUSE_RELAY_ON_LOW_BATTERY to true,
        ),
        peersNearby = 8,
        txRate = "12.8 KB/s",
        rxRate = "44.1 KB/s",
        roundTrip = "18 ms",
        linkActivity = listOf(0.3f, 0.55f, 0.9f, 0.6f, 1f, 0.45f, 0.8f, 0.25f, 0.7f, 0.95f, 0.5f, 0.35f),
        relayUsedMb = 42,
        relayQuotaMb = 500,
        relayResetsIn = "07:18:22",
    )
}
