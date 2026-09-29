package app.tfl.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.R
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import kotlinx.serialization.Serializable

@Serializable
internal data object DesignCatalogRoute

/**
 * Developer tools compiled into debug builds only. Release builds compile the no-op twin in
 * `src/release`, so none of this code or its strings ships.
 */
internal object DebugTools {

    // Off at every launch and never persisted: FLAG_SECURE stays the default even in debug builds.
    private val allowScreenshotsState = mutableStateOf(false)

    val allowScreenshots: State<Boolean> get() = allowScreenshotsState

    /** Rows appended to the Settings list. */
    fun settingsSection(navController: NavController): (@Composable () -> Unit)? = {
        DeveloperSection(
            onOpenCatalog = { navController.navigate(DesignCatalogRoute) },
            allowScreenshots = allowScreenshotsState.value,
            onAllowScreenshotsChange = { allowScreenshotsState.value = it },
        )
    }

    fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) {
        composable<DesignCatalogRoute> { DesignCatalogScreen(onBack = onBack) }
    }
}

@Composable
private fun DeveloperSection(
    onOpenCatalog: () -> Unit,
    allowScreenshots: Boolean,
    onAllowScreenshotsChange: (Boolean) -> Unit,
) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.debug_section),
            icon = MaterialSymbols.DeveloperMode,
            iconTint = TflTheme.colors.warning,
        )
        TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            ListRow(
                title = stringResource(R.string.debug_catalog_title),
                subtitle = stringResource(R.string.debug_catalog_subtitle),
                icon = MaterialSymbols.Palette,
                standalone = false,
                onClick = onOpenCatalog,
            )
            TflDivider()
            ToggleRow(
                title = stringResource(R.string.debug_screenshots_title),
                subtitle = stringResource(R.string.debug_screenshots_subtitle),
                icon = MaterialSymbols.ScreenshotMonitor,
                checked = allowScreenshots,
                onCheckedChange = onAllowScreenshotsChange,
            )
        }
    }
}
