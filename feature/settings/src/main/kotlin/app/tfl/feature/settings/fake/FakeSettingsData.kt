package app.tfl.feature.settings.fake

import app.tfl.feature.settings.NetworkPreview

/**
 * PLACEHOLDER FIGURES for the Network screen's relay quota, adapted from the Stitch mock and
 * labelled as sample data on screen. Real figures come with relaying (Phase 6).
 */
internal object FakeSettingsData {
    val networkPreview = NetworkPreview(
        relayUsedMb = 42,
        relayQuotaMb = 500,
        relayResetsIn = "07:18:22",
    )
}
