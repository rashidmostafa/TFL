@file:Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")

package app.tfl.feature.contacts.debug

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/** Release builds: no contacts developer tools (no fake friends, key-change simulation or test codes). */
object ContactsDebugTools {
    @Composable
    fun ProfileTools(contactId: Long, onKeyChanged: () -> Unit) = Unit

    @Composable
    fun ScanTools(onCode: (String) -> Unit) = Unit

    fun NavGraphBuilder.debugDestinations(navController: NavController) = Unit
}
