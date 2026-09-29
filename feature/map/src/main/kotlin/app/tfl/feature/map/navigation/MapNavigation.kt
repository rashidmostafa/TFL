package app.tfl.feature.map.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.feature.map.MapScreen
import kotlinx.serialization.Serializable

@Serializable
data object MapRoute

fun NavGraphBuilder.mapScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
) {
    composable<MapRoute> {
        MapScreen(onOpenProfile = onOpenProfile, onNotYetAvailable = onNotYetAvailable)
    }
}
