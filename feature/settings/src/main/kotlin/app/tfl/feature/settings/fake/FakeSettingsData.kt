package app.tfl.feature.settings.fake

import app.tfl.feature.settings.NetworkPreview

/**
 * PLACEHOLDER FIGURES for the Network screen's mesh card, adapted from the Stitch mock and labelled
 * as sample data on screen. Real figures come from the transports (Phases 3 and 4).
 */
internal object FakeSettingsData {
    val networkPreview = NetworkPreview(
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
