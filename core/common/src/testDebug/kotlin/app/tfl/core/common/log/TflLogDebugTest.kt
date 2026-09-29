package app.tfl.core.common.log

import org.junit.Assert.assertTrue
import org.junit.Test

class TflLogDebugTest {

    @Test
    fun `logging is enabled in debug`() {
        assertTrue(TflLog.isEnabled)
    }
}
