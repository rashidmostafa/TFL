package app.tfl

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.tfl.core.transport.TransportController
import app.tfl.core.transport.service.ScheduledSendWorker
import javax.inject.Inject
import javax.inject.Provider

/** Builds TFL's workers with what they need (WorkManager can't inject them by itself). */
class TflWorkerFactory @Inject constructor(private val transport: Provider<TransportController>) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        when (workerClassName) {
            ScheduledSendWorker::class.java.name -> ScheduledSendWorker(appContext, workerParameters, transport.get())
            else -> null
        }
}
