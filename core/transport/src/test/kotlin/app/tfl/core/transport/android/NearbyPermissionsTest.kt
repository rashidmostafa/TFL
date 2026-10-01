package app.tfl.core.transport.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Nearby asks for on each Android version (Google's list for Nearby Connections). */
class NearbyPermissionsTest {

    private val location = "android.permission.ACCESS_FINE_LOCATION"
    private val approximate = "android.permission.ACCESS_COARSE_LOCATION"
    private val bluetooth = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE", "android.permission.BLUETOOTH_CONNECT")
    private val wifi = "android.permission.NEARBY_WIFI_DEVICES"
    private val localNetwork = "android.permission.ACCESS_LOCAL_NETWORK"
    private val notifications = "android.permission.POST_NOTIFICATIONS"

    @Test
    fun `Android 8 and 9 need approximate location, switched on`() {
        for (sdk in 26..28) {
            assertEquals(listOf(approximate), NearbyPermissions.required(sdk))
            assertTrue(NearbyPermissions.locationSwitchNeeded(sdk))
        }
    }

    @Test
    fun `Android 10 and 11 (the M20) need precise location, switched on`() {
        for (sdk in 29..30) {
            assertEquals(listOf(location), NearbyPermissions.required(sdk))
            assertEquals(listOf(location), NearbyPermissions.toRequest(sdk))
            assertTrue(NearbyPermissions.locationSwitchNeeded(sdk))
        }
    }

    @Test
    fun `Android 12 (the M51) needs Bluetooth and precise location, asked with approximate`() {
        assertEquals(bluetooth + location, NearbyPermissions.required(31))
        assertEquals(bluetooth + location + approximate, NearbyPermissions.toRequest(31))
        assertFalse(NearbyPermissions.locationSwitchNeeded(31))
    }

    @Test
    fun `from Android 12L no location, and Nearby Wi-Fi with notifications from 13`() {
        // NEARBY_WIFI_DEVICES doesn't exist on 12L: asking for it there would never be granted.
        assertEquals(bluetooth, NearbyPermissions.required(32))
        assertEquals(bluetooth, NearbyPermissions.toRequest(32))
        assertFalse(NearbyPermissions.locationSwitchNeeded(32))
        for (sdk in 33..36) {
            assertEquals(bluetooth + wifi, NearbyPermissions.required(sdk))
            assertEquals(bluetooth + wifi + notifications, NearbyPermissions.toRequest(sdk))
            assertFalse(NearbyPermissions.locationSwitchNeeded(sdk))
        }
    }

    @Test
    fun `Android 17 adds the local network permission`() {
        assertEquals(bluetooth + wifi + localNetwork, NearbyPermissions.required(37))
        assertEquals(bluetooth + wifi + localNetwork + notifications, NearbyPermissions.toRequest(37))
    }

    @Test
    fun `nothing ever asks for internet or background location`() {
        for (sdk in 26..37) {
            val asked = NearbyPermissions.toRequest(sdk)
            assertFalse("android.permission.INTERNET" in asked)
            assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in asked)
        }
    }

    @Test
    fun `ready means permitted, Bluetooth on, and Location on where needed`() {
        val ready = NearbyStatus(missing = emptyList(), bluetoothOn = true, locationNeeded = true, locationOn = true)
        assertTrue(ready.ready)
        assertFalse(ready.copy(locationOn = false).ready)
        assertTrue(ready.copy(locationOn = false, locationNeeded = false).ready)
        assertFalse(ready.copy(bluetoothOn = false).ready)
        assertTrue(ready.copy(bluetoothOn = false).permitted)
        assertFalse(ready.copy(missing = listOf(location)).permitted)
    }
}
