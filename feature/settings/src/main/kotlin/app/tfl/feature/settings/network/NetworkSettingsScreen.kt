package app.tfl.feature.settings.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.StatusPill
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflProgressBar
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.designsystem.component.RadioRow
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.model.network.BridgeMode
import app.tfl.feature.settings.NetworkPreview
import app.tfl.feature.settings.NetworkSettingsUiState
import app.tfl.feature.settings.NetworkSettingsViewModel
import app.tfl.feature.settings.NetworkToggle
import app.tfl.feature.settings.R
import app.tfl.feature.settings.security.GroupCard

@Composable
internal fun NetworkSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NetworkSettingsContent(uiState, onBack, viewModel::onToggle, onShowBridges = { viewModel.showBridges(true) }, modifier = modifier)
    if (uiState.showBridges) {
        BridgeSheet(uiState.bridgeMode, onSelect = viewModel::onBridgeSelect, onDismiss = { viewModel.showBridges(false) })
    }
}

@Composable
internal fun NetworkSettingsContent(
    uiState: NetworkSettingsUiState,
    onBack: () -> Unit,
    onToggle: (NetworkToggle, Boolean) -> Unit,
    onShowBridges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.network_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Callout(
                text = stringResource(R.string.network_preview_body),
                title = stringResource(R.string.settings_preview_title),
                tone = CalloutTone.Warning,
            )
            MeshStatusCard(uiState.preview)

            SectionHeader(
                stringResource(R.string.network_section_tor),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.VpnLock,
                iconTint = colors.tor,
            )
            GroupCard {
                ToggleRow(
                    title = stringResource(R.string.network_tor_title),
                    subtitle = stringResource(R.string.network_tor_subtitle),
                    checked = uiState.isOn(NetworkToggle.TOR),
                    onCheckedChange = { onToggle(NetworkToggle.TOR, it) },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.network_bridges_title),
                    subtitle = stringResource(uiState.bridgeMode.label().first),
                    standalone = false,
                    onClick = onShowBridges,
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.network_tor_only_title),
                    subtitle = stringResource(R.string.network_tor_only_subtitle),
                    standalone = false,
                    trailing = { Tag(stringResource(R.string.settings_always_on), color = colors.success) },
                )
            }

            SectionHeader(
                stringResource(R.string.network_section_mesh),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Hub,
                iconTint = colors.mesh,
            )
            GroupCard {
                ToggleRow(
                    title = stringResource(R.string.network_nearby_title),
                    subtitle = stringResource(R.string.network_nearby_subtitle),
                    checked = uiState.isOn(NetworkToggle.NEARBY),
                    onCheckedChange = { onToggle(NetworkToggle.NEARBY, it) },
                )
                TflDivider()
                ToggleRow(
                    title = stringResource(R.string.network_relay_title),
                    subtitle = stringResource(R.string.network_relay_subtitle),
                    checked = uiState.isOn(NetworkToggle.RELAY),
                    onCheckedChange = { onToggle(NetworkToggle.RELAY, it) },
                )
                RelayQuota(uiState.preview)
            }

            SectionHeader(
                stringResource(R.string.network_section_battery),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Bolt,
                iconTint = colors.warning,
            )
            GroupCard {
                ToggleRow(
                    title = stringResource(R.string.network_battery_saver_title),
                    subtitle = stringResource(R.string.network_battery_saver_subtitle),
                    checked = uiState.isOn(NetworkToggle.BATTERY_SAVER),
                    onCheckedChange = { onToggle(NetworkToggle.BATTERY_SAVER, it) },
                )
                TflDivider()
                ToggleRow(
                    title = stringResource(R.string.network_low_battery_title),
                    subtitle = stringResource(R.string.network_low_battery_subtitle),
                    checked = uiState.isOn(NetworkToggle.PAUSE_RELAY_ON_LOW_BATTERY),
                    onCheckedChange = { onToggle(NetworkToggle.PAUSE_RELAY_ON_LOW_BATTERY, it) },
                )
            }
        }
    }
}

/** Sample figures only, labelled as such, until the transports exist. */
@Composable
private fun MeshStatusCard(uiState: NetworkPreview) {
    val colors = TflTheme.colors
    TflCard(modifier = Modifier.padding(top = 4.dp), borderColor = colors.primary.copy(alpha = 0.25f), verticalSpacing = 10.dp) {
        StatusPill(stringResource(R.string.network_mesh_status), color = colors.warning)
        Text(stringResource(R.string.network_mesh_title), style = TflTheme.typography.headlineSm, color = colors.textPrimary)
        Text(
            text = pluralStringResource(R.plurals.network_peers_nearby, uiState.peersNearby, uiState.peersNearby),
            style = TflTheme.typography.codeSm,
            color = colors.primary,
        )
        // Recent link activity; decorative, the figures below carry the information.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            uiState.linkActivity.forEach { level ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(level.coerceIn(0.1f, 1f))
                        .background(if (level > 0.6f) colors.primary else colors.mesh.copy(alpha = 0.5f), TflTheme.shapes.pill),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.network_tx, uiState.txRate), style = TflTheme.typography.codeSm, color = colors.textMuted)
            Text(stringResource(R.string.network_rtt, uiState.roundTrip), style = TflTheme.typography.codeSm, color = colors.primary)
            Text(stringResource(R.string.network_rx, uiState.rxRate), style = TflTheme.typography.codeSm, color = colors.textMuted)
        }
    }
}

@Composable
private fun RelayQuota(uiState: NetworkPreview) {
    val colors = TflTheme.colors
    Column(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            .fillMaxWidth()
            .background(colors.canvas, TflTheme.shapes.keyBlock)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.network_relay_quota), style = TflTheme.typography.codeSm, color = colors.textPrimary)
            Text(
                stringResource(R.string.network_relay_used, uiState.relayUsedMb, uiState.relayQuotaMb),
                style = TflTheme.typography.codeSm,
                color = colors.primary,
            )
        }
        TflProgressBar(progress = uiState.relayUsedMb.toFloat() / uiState.relayQuotaMb)
        Text(stringResource(R.string.network_relay_resets, uiState.relayResetsIn), style = TflTheme.typography.codeSm, color = colors.textMuted)
    }
}

/** Title and explanation of each way to reach Tor. */
internal fun BridgeMode.label(): Pair<Int, Int> = when (this) {
    BridgeMode.DIRECT -> R.string.network_bridge_direct to R.string.network_bridge_direct_body
    BridgeMode.OBFS4 -> R.string.network_bridge_obfs4 to R.string.network_bridge_obfs4_body
    BridgeMode.SNOWFLAKE -> R.string.network_bridge_snowflake to R.string.network_bridge_snowflake_body
}

@Composable
private fun BridgeSheet(selected: BridgeMode, onSelect: (BridgeMode) -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.network_bridges_title), icon = MaterialSymbols.VpnLock, onClose = onDismiss)
        Column(Modifier.padding(bottom = 16.dp)) {
            BridgeMode.entries.forEach { mode ->
                val (title, body) = mode.label()
                RadioRow(stringResource(title), selected = mode == selected, onSelect = { onSelect(mode) }, subtitle = stringResource(body))
            }
        }
    }
}
