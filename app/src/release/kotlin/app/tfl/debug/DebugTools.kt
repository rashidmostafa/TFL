@file:Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")

package app.tfl.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/** Release builds: no developer tools, and screenshots can never be allowed. */
internal object DebugTools {
    val allowScreenshots: State<Boolean> = mutableStateOf(false)

    fun settingsSection(navController: NavController): (@Composable () -> Unit)? = null

    fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) = Unit
}
