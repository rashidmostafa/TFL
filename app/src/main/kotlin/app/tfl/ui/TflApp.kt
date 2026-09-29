package app.tfl.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tfl.R
import app.tfl.core.designsystem.component.TflNavigationBar
import app.tfl.core.designsystem.component.TflNavigationBarItem
import app.tfl.core.designsystem.component.TflSnackbarHost
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.navigation.TflNavHost
import app.tfl.navigation.TopLevelDestination
import kotlinx.coroutines.launch

/**
 * The app shell: bottom navigation, the snackbar, and every screen.
 * Each screen draws its own top bar below the status bar.
 */
@Composable
fun TflApp(
    appState: TflAppState,
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notYetAvailable = stringResource(R.string.not_yet_available)
    val onNotYetAvailable: () -> Unit = remember(scope, snackbarHostState, notYetAvailable) {
        {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(notYetAvailable)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = TflTheme.colors.canvas,
        contentColor = TflTheme.colors.textPrimary,
        // Status-bar insets are handled by each screen's top bar.
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { TflSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (appState.showBottomBar) {
                TflNavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        TflNavigationBarItem(
                            selected = appState.isSelected(destination),
                            onClick = { appState.navigateTo(destination) },
                            symbol = destination.symbol,
                            label = stringResource(destination.label),
                        )
                    }
                }
            }
        },
    ) { padding ->
        TflNavHost(
            appState = appState,
            appVersion = appVersion,
            onNotYetAvailable = onNotYetAvailable,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}
