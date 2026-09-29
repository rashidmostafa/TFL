package app.tfl.core.common.log

import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class TflLogReleaseTest {

    @Test
    fun `logging is disabled in release`() {
        assertFalse(TflLog.isEnabled)
    }

    @Test
    fun `release never builds log messages`() {
        val neverCalled = { fail("A log message was built in a release build"); "" }
        TflLog.d("test", neverCalled)
        TflLog.i("test", neverCalled)
        TflLog.w("test", IllegalStateException(), neverCalled)
        TflLog.e("test", IllegalStateException(), neverCalled)
    }
}
