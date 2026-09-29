package app.tfl.feature.map

import androidx.compose.runtime.Immutable
import app.tfl.core.model.Transport

/** A friend currently sharing their location with you. */
@Immutable
data class FriendLocation(
    val id: String,
    val name: String,
    /** First name or nickname, used on the map marker. */
    val shortName: String,
    val initials: String,
    val verified: Boolean,
    val distance: String,
    val place: String,
    val timeLeft: String,
    val batteryPercent: Int,
    val transport: Transport,
    val transportDetail: String? = null,
    val updated: String? = null,
    /** Short label under the map marker, e.g. "45m". */
    val markerLabel: String,
    /** Marker position on the placeholder map, as fractions of its width and height. */
    val mapX: Float,
    val mapY: Float,
)

@Immutable
data class MapUiState(
    val peerCount: Int,
    val coordinates: String,
    val sharingCount: Int,
    val friends: List<FriendLocation>,
    val hasQuery: Boolean,
)

internal fun List<FriendLocation>.search(query: String): List<FriendLocation> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return filter { it.name.contains(needle, ignoreCase = true) || it.place.contains(needle, ignoreCase = true) }
}
