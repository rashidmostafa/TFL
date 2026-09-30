package app.tfl.core.crypto.lock

import app.tfl.core.testing.crypto.FakeDeviceClock
import org.junit.Assert.assertEquals
import org.junit.Test

class AttemptPolicyTest {

    private val clock = FakeDeviceClock()

    private fun afterFailures(count: Int): AttemptRecord {
        var record = AttemptRecord.NONE
        repeat(count) { record = AttemptPolicy.failed(record, clock) }
        return record
    }

    @Test
    fun `delays escalate after the fourth failure`() {
        assertEquals(listOf(0L, 0L, 0L, 0L), (1..4).map(AttemptPolicy::delayAfter))
        assertEquals(30_000L, AttemptPolicy.delayAfter(5))
        assertEquals(60_000L, AttemptPolicy.delayAfter(6))
        assertEquals(300_000L, AttemptPolicy.delayAfter(7))
        assertEquals(900_000L, AttemptPolicy.delayAfter(8))
        assertEquals(3_600_000L, AttemptPolicy.delayAfter(9))
        assertEquals(3_600_000L, AttemptPolicy.delayAfter(30))
    }

    @Test
    fun `the wait counts down on the boot clock`() {
        val record = afterFailures(5)
        assertEquals(30_000L, AttemptPolicy.remainingDelay(record, clock))
        clock.advance(20_000)
        assertEquals(10_000L, AttemptPolicy.remainingDelay(record, clock))
        clock.advance(10_000)
        assertEquals(0L, AttemptPolicy.remainingDelay(record, clock))
    }

    @Test
    fun `rebooting restarts the wait instead of skipping it`() {
        val record = afterFailures(6)
        clock.advance(59_000)
        clock.reboot(elapsedAfterBoot = 5_000)
        assertEquals(55_000L, AttemptPolicy.remainingDelay(record, clock))
    }

    @Test
    fun `no wait before the fifth failure`() {
        assertEquals(0L, AttemptPolicy.remainingDelay(afterFailures(4), clock))
    }
}
