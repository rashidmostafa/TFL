package app.tfl.core.transport.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.transport.ScheduledWakeup
import app.tfl.core.transport.TransportController
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Wakes the outbox when a scheduled message falls due, in case the process slept through its own
 * timer. The message was sealed when scheduled, so the transport can send it even while TFL is
 * locked; after a reboot it waits for the next unlock (the keys were in memory).
 */
class ScheduledSendWorker(
    context: Context,
    params: WorkerParameters,
    private val controller: TransportController,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        controller.wake()
        return Result.success()
    }
}

/**
 * One WorkManager job for the next scheduled message. WorkManager's own (unencrypted) database
 * then holds only when that is, nothing about the message.
 */
class WorkManagerWakeup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: DeviceClock,
) : ScheduledWakeup {
    override fun set(atMillis: Long?) {
        val work = WorkManager.getInstance(context)
        if (atMillis == null) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInitialDelay((atMillis - clock.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val NAME = "tfl.scheduled"
    }
}
