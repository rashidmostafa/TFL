package app.tfl.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.tfl.R
import app.tfl.core.crypto.lock.KdfBenchmark
import app.tfl.core.crypto.lock.KdfBenchmarker
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.ListRowTone
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.session.AppSession
import app.tfl.feature.contacts.debug.ContactsDebugTools
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal data object DesignCatalogRoute

@Serializable
internal data object TransportLogRoute

/** What the developer tools need from the app graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugEntryPoint {
    fun kdfBenchmarker(): KdfBenchmarker
    fun session(): AppSession
}

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
            contactsTools = { ContactsDebugTools.DeveloperRows(navController) },
            transportTools = { TransportDebugRows(onOpenLog = { navController.navigate(TransportLogRoute) }) },
        )
    }

    fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) {
        composable<DesignCatalogRoute> { DesignCatalogScreen(onBack = onBack) }
        composable<TransportLogRoute> { TransportLogScreen(onBack = onBack) }
    }
}

@Composable
private fun DeveloperSection(
    onOpenCatalog: () -> Unit,
    allowScreenshots: Boolean,
    onAllowScreenshotsChange: (Boolean) -> Unit,
    contactsTools: @Composable () -> Unit,
    transportTools: @Composable () -> Unit,
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
            TflDivider()
            contactsTools()
            TflDivider()
            transportTools()
            TflDivider()
            SecurityTools()
        }
    }
}

/** The on-device Argon2id benchmark (to choose PIN key costs) and an immediate wipe. */
@Composable
private fun SecurityTools() {
    val context = LocalContext.current
    val tools = remember(context) { EntryPointAccessors.fromApplication(context, DebugEntryPoint::class.java) }
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<KdfBenchmark>?>(null) }
    var measuring by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    ListRow(
        title = stringResource(R.string.debug_benchmark_title),
        subtitle = stringResource(if (measuring) R.string.debug_benchmark_running else R.string.debug_benchmark_subtitle),
        icon = MaterialSymbols.Speed,
        standalone = false,
        onClick = {
            if (!measuring) {
                measuring = true
                scope.launch {
                    results = withContext(Dispatchers.Default) { tools.kdfBenchmarker().measure() }
                    measuring = false
                }
            }
        },
    )
    results?.let { measured ->
        Text(
            text = measured.joinToString("\n") { result ->
                val cost = "Argon2id ${result.params.opsLimit} passes × ${result.params.memLimitMebibytes} MiB"
                result.millis?.let { "$cost: $it ms" } ?: "$cost: failed (out of memory?)"
            },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            style = TflTheme.typography.codeSm,
            color = TflTheme.colors.primary,
        )
    }
    TflDivider()
    ListRow(
        title = stringResource(R.string.debug_wipe_title),
        subtitle = stringResource(R.string.debug_wipe_subtitle),
        icon = MaterialSymbols.DeleteForever,
        tone = ListRowTone.Danger,
        standalone = false,
        onClick = { confirmWipe = true },
    )
    if (confirmWipe) {
        TflModalBottomSheet(onDismissRequest = { confirmWipe = false }) {
            SheetHeader(stringResource(R.string.debug_wipe_confirm_title), icon = MaterialSymbols.DeleteForever)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.debug_wipe_confirm_body), style = TflTheme.typography.bodyMd, color = TflTheme.colors.textMuted)
                DestructiveButton(
                    stringResource(R.string.debug_wipe_confirm),
                    onClick = { scope.launch { tools.session().wipe() } },
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostButton(stringResource(R.string.debug_wipe_cancel), onClick = { confirmWipe = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
