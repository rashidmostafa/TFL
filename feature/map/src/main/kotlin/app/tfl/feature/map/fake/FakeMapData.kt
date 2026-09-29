package app.tfl.feature.map.fake

import app.tfl.core.model.Transport
import app.tfl.feature.map.FriendLocation

/**
 * PHASE 0 PLACEHOLDER DATA adapted from the Stitch mock. Live location sharing and offline maps
 * arrive in Phase 8; nothing here is real, including the coordinates.
 */
internal object FakeMapData {
    const val PEER_COUNT = 12
    const val COORDINATES = "52.5200° N, 13.4050° E"

    val friends = listOf(
        FriendLocation(
            id = "elena",
            name = "Elena Vance",
            shortName = "Elena",
            initials = "EV",
            verified = true,
            distance = "140 m away",
            place = "South-East",
            timeLeft = "45m left",
            batteryPercent = 78,
            transport = Transport.NEARBY,
            transportDetail = "Direct",
            markerLabel = "45m",
            mapX = 0.2f,
            mapY = 0.3f,
        ),
        FriendLocation(
            id = "alex",
            name = "Alex",
            shortName = "Alex",
            initials = "AL",
            verified = true,
            distance = "1.2 km away",
            place = "Ridge Point",
            timeLeft = "1h 15m left",
            batteryPercent = 84,
            transport = Transport.MESH,
            transportDetail = "2 hops",
            updated = "Updated 2m ago",
            markerLabel = "1h",
            mapX = 0.76f,
            mapY = 0.2f,
        ),
        FriendLocation(
            id = "maya",
            name = "Dr. Maya Lin",
            shortName = "Maya",
            initials = "ML",
            verified = false,
            distance = "3.8 km away",
            place = "Metro Depot",
            timeLeft = "12m left",
            batteryPercent = 42,
            transport = Transport.TOR,
            updated = "Updated 8m ago",
            markerLabel = "12m",
            mapX = 0.7f,
            mapY = 0.64f,
        ),
    )
}
