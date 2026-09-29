package app.tfl.feature.tools.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.feature.tools.ToolsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ToolsRoute

fun NavGraphBuilder.toolsScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
) {
    composable<ToolsRoute> {
        ToolsScreen(onOpenProfile = onOpenProfile, onNotYetAvailable = onNotYetAvailable)
    }
}
