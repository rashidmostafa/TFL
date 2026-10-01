package app.tfl.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The release app carries none of the transport's developer tools, nor their text. */
class ReleaseTransportDebugToolsTest {

    @Test
    fun noSimulatedFriendCombinedRadioOrTransportLog() {
        listOf(
            "app.tfl.core.transport.debug.SimulatedFriend",
            "app.tfl.core.transport.debug.SimulatedAir",
            "app.tfl.core.transport.debug.CombinedRadio",
            "app.tfl.core.transport.debug.TransportEvents",
            "app.tfl.debug.TransportDebugEntryPoint",
        ).forEach { name -> assertThrows(name, ClassNotFoundException::class.java) { Class.forName(name) } }
    }

    @Test
    fun noDebugStrings() {
        val strings = app.tfl.R.string::class.java.fields.map { it.name }
        assertTrue("sanity: this is the app's R class", "app_name" in strings)
        assertFalse(strings.any { it.startsWith("debug_") })
    }
}
