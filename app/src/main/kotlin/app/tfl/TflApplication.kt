package app.tfl

import android.app.Application
import androidx.work.Configuration
import app.tfl.core.session.AutoLock
import app.tfl.core.transport.TransportController
import app.tfl.core.transport.android.TransportNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TflApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var autoLock: AutoLock

    @Inject
    lateinit var transport: TransportController

    @Inject
    lateinit var notifications: TransportNotifications

    @Inject
    lateinit var workerFactory: TflWorkerFactory

    /** WorkManager starts from here rather than by itself (see the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        autoLock.install(this)
        notifications.createChannels()
        notifications.followDisguise()
        // Idle until an unlock hands it keys.
        transport.start()
    }
}
