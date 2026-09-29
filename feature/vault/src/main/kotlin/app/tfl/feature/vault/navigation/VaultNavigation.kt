package app.tfl.feature.vault.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.feature.vault.VaultScreen
import kotlinx.serialization.Serializable

@Serializable
data object VaultRoute

fun NavGraphBuilder.vaultScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
) {
    composable<VaultRoute> {
        VaultScreen(onOpenProfile = onOpenProfile, onNotYetAvailable = onNotYetAvailable)
    }
}
