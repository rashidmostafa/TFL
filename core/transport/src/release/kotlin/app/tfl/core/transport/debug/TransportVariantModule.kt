package app.tfl.core.transport.debug

import app.tfl.core.transport.TransportLog
import app.tfl.core.transport.radio.GoogleNearbyRadio
import app.tfl.core.transport.radio.Radio
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds: the plain radio, and no transport log. */
@Module
@InstallIn(SingletonComponent::class)
object TransportVariantModule {
    @Provides
    fun radio(google: GoogleNearbyRadio): Radio = google

    @Provides
    fun log(): TransportLog = TransportLog.NONE
}
