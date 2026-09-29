package app.tfl.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.ListRowTone
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SecureBadge
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.settings.components.IdentitySheet

@Composable
internal fun SettingsScreen(
    appVersion: String,
    onOpenSecurity: () -> Unit,
    onOpenNetwork: () -> Unit,
    onNotYetAvailable: () -> Unit,
    developerSection: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showIdentity by rememberSaveable { mutableStateOf(false) }
    SettingsContent(
        uiState = uiState,
        appVersion = appVersion,
        onShowIdentity = { showIdentity = true },
        onOpenSecurity = onOpenSecurity,
        onOpenNetwork = onOpenNetwork,
        onNotYetAvailable = onNotYetAvailable,
        developerSection = developerSection,
        modifier = modifier,
    )
    if (showIdentity) {
        IdentitySheet(fingerprint = uiState.fingerprint, onDismiss = { showIdentity = false })
    }
}

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    appVersion: String,
    onShowIdentity: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenNetwork: () -> Unit,
    onNotYetAvailable: () -> Unit,
    developerSection: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.settings_title),
            actions = { SecureBadge() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IdentityCard(uiState, onShowIdentity = onShowIdentity, onCopy = onNotYetAvailable)
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    value = stringResource(if (uiState.meshRelayOn) R.string.settings_stat_mesh_value else R.string.settings_stat_mesh_off),
                    label = stringResource(R.string.settings_stat_mesh_label),
                    color = colors.mesh,
                    icon = MaterialSymbols.Hub,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stringResource(R.string.settings_stat_tor_value),
                    label = stringResource(R.string.settings_stat_tor_label),
                    color = colors.tor,
                    icon = MaterialSymbols.VpnLock,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stringResource(R.string.settings_stat_vault_value),
                    label = stringResource(R.string.settings_stat_vault_label),
                    color = colors.primary,
                    icon = MaterialSymbols.Memory,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionHeader(stringResource(R.string.settings_section_configuration), modifier = Modifier.padding(vertical = 4.dp))
            ListRow(
                title = stringResource(R.string.settings_account_title),
                subtitle = stringResource(R.string.settings_account_subtitle),
                icon = MaterialSymbols.ManageAccounts,
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.settings_privacy_title),
                subtitle = stringResource(R.string.settings_privacy_subtitle),
                icon = MaterialSymbols.VisibilityOff,
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.settings_security_title),
                subtitle = stringResource(R.string.settings_security_subtitle),
                icon = MaterialSymbols.Security,
                onClick = onOpenSecurity,
            )
            ListRow(
                title = stringResource(R.string.settings_network_title),
                subtitle = stringResource(R.string.settings_network_subtitle),
                icon = MaterialSymbols.CellTower,
                titleBadge = if (uiState.torConnected) {
                    { Tag(stringResource(R.string.settings_network_badge), color = colors.tor) }
                } else {
                    null
                },
                onClick = onOpenNetwork,
            )
            ListRow(
                title = stringResource(R.string.settings_bandwidth_title),
                subtitle = stringResource(R.string.settings_bandwidth_subtitle),
                icon = MaterialSymbols.Tune,
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.settings_vault_title),
                subtitle = uiState.vaultUsage,
                icon = MaterialSymbols.Lock,
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.settings_notifications_title),
                subtitle = stringResource(R.string.settings_notifications_subtitle),
                icon = MaterialSymbols.NotificationsActive,
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_subtitle, appVersion),
                icon = MaterialSymbols.Info,
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.settings_panic_title),
                subtitle = stringResource(R.string.settings_panic_subtitle),
                icon = MaterialSymbols.Warning,
                tone = ListRowTone.Danger,
                modifier = Modifier.padding(top = 8.dp),
                onClick = onNotYetAvailable,
            )
            developerSection?.invoke()
        }
    }
}

@Composable
private fun IdentityCard(uiState: SettingsUiState, onShowIdentity: () -> Unit, onCopy: () -> Unit) {
    val colors = TflTheme.colors
    TflCard(borderColor = colors.primary.copy(alpha = 0.25f), verticalSpacing = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(colors.surfaceContainer, CircleShape)
                        .border(1.dp, colors.primary.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    TflIcon(MaterialSymbols.ShieldPerson, contentDescription = null, size = 32.dp, tint = colors.primary)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(colors.surface, CircleShape)
                        .padding(3.dp)
                        .background(colors.primary, CircleShape),
                )
            }
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(uiState.displayName, style = TflTheme.typography.headlineMd, color = colors.textPrimary)
                    TflIcon(MaterialSymbols.Verified, contentDescription = null, size = 18.dp, tint = colors.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TflIcon(MaterialSymbols.Fingerprint, contentDescription = null, size = 14.dp, tint = colors.textMuted)
                    Text(
                        text = stringResource(R.string.settings_fingerprint_short, uiState.fingerprint.first(), uiState.fingerprint.last()),
                        style = TflTheme.typography.codeSm,
                        color = colors.textMuted,
                    )
                }
                StatusLine(stringResource(R.string.settings_reachable))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                text = stringResource(R.string.settings_public_identity),
                onClick = onShowIdentity,
                modifier = Modifier.weight(1f),
                icon = MaterialSymbols.QrCode2,
                size = TflButtonSize.Medium,
            )
            TflIconButton(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = stringResource(R.string.settings_copy_fingerprint),
                onClick = onCopy,
                size = 48.dp,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, icon: String, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    TflCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalSpacing = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TflIcon(icon, contentDescription = null, size = 14.dp, tint = color)
            Text(value, style = TflTheme.typography.codeSm, color = color, maxLines = 1)
        }
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = TflTheme.typography.labelSm,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
