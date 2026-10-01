package app.tfl.core.transport.di

import app.tfl.core.transport.MessageAlerts
import app.tfl.core.transport.NearbyReadiness
import app.tfl.core.transport.PowerSaving
import app.tfl.core.transport.ScheduledWakeup
import app.tfl.core.transport.TransportController
import app.tfl.core.transport.TransportStatus
import app.tfl.core.transport.TransportService
import app.tfl.core.transport.android.AndroidMessageAlerts
import app.tfl.core.transport.android.AndroidNearbyReadiness
import app.tfl.core.transport.android.NearbySetup
import app.tfl.core.transport.android.AndroidPowerSaving
import app.tfl.core.transport.service.AndroidTransportService
import app.tfl.core.transport.service.WorkManagerWakeup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TransportBindings {
    @Binds
    abstract fun readiness(impl: AndroidNearbyReadiness): NearbyReadiness

    @Binds
    abstract fun setup(impl: AndroidNearbyReadiness): NearbySetup

    @Binds
    abstract fun powerSaving(impl: AndroidPowerSaving): PowerSaving

    @Binds
    abstract fun service(impl: AndroidTransportService): TransportService

    @Binds
    abstract fun alerts(impl: AndroidMessageAlerts): MessageAlerts

    @Binds
    abstract fun wakeup(impl: WorkManagerWakeup): ScheduledWakeup

    @Binds
    abstract fun status(impl: TransportController): TransportStatus
}
