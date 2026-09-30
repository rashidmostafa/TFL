package app.tfl.core.crypto.lock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Time since boot plus the boot count: neither can be changed by adjusting the phone's clock. */
interface DeviceClock {
    fun elapsedRealtime(): Long
    fun bootCount(): Int
}

class AndroidDeviceClock @Inject constructor(@ApplicationContext private val context: Context) : DeviceClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}

/** Escalating waits after consecutive wrong PINs. */
object AttemptPolicy {
    private const val SECOND = 1_000L
    private const val MINUTE = 60 * SECOND

    /** Wait required after [failures] consecutive failures. */
    fun delayAfter(failures: Int): Long = when {
        failures < 5 -> 0
        failures == 5 -> 30 * SECOND
        failures == 6 -> MINUTE
        failures == 7 -> 5 * MINUTE
        failures == 8 -> 15 * MINUTE
        else -> 60 * MINUTE
    }

    /** Milliseconds until the next attempt is allowed. After a reboot the wait restarts from boot. */
    fun remainingDelay(record: AttemptRecord, clock: DeviceClock): Long {
        val delay = delayAfter(record.failures)
        if (delay == 0L) return 0
        val start = if (clock.bootCount() == record.bootCount) record.elapsedAtLastFailure else 0L
        return (start + delay - clock.elapsedRealtime()).coerceAtLeast(0)
    }

    fun failed(record: AttemptRecord, clock: DeviceClock) = AttemptRecord(
        failures = record.failures + 1,
        bootCount = clock.bootCount(),
        elapsedAtLastFailure = clock.elapsedRealtime(),
    )
}
