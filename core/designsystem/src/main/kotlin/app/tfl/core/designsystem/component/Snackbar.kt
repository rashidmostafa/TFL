package app.tfl.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.theme.TflTheme

/** Snackbars as elevated dark containers instead of Material's light inverse surface. */
@Composable
fun TflSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val shape = TflTheme.shapes.control
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            modifier = Modifier.border(1.dp, colors.borderElevated, shape),
            shape = shape,
            containerColor = colors.surfaceContainer,
            contentColor = colors.textPrimary,
            actionContentColor = colors.primary,
        ) {
            Text(data.visuals.message, style = TflTheme.typography.bodyMd)
        }
    }
}
