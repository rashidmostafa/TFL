package app.tfl.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.BarSegment
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.ProfileButton
import app.tfl.core.designsystem.component.SecureBadge
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SegmentedBar
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflColors
import app.tfl.core.designsystem.theme.TflTheme

@Composable
internal fun VaultScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VaultContent(uiState, onOpenProfile, onNotYetAvailable, modifier)
}

@Composable
internal fun VaultContent(
    uiState: VaultUiState,
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.vault_title),
            subtitle = { StatusLine(stringResource(R.string.vault_status)) },
            navigationIcon = { IconTile(MaterialSymbols.ShieldLock, size = 36.dp, iconSize = 20.dp) },
            actions = {
                SecureBadge(label = stringResource(R.string.vault_encrypted))
                ProfileButton(onClick = onOpenProfile)
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionCard(uiState.autoLockIn, onLock = onNotYetAvailable)
            StorageCard(uiState)
            SectionHeader(
                title = stringResource(R.string.vault_section_areas),
                modifier = Modifier.padding(top = 4.dp),
                trailing = pluralStringResource(R.plurals.vault_section_areas_count, uiState.areas.size, uiState.areas.size),
                trailingColor = colors.primary,
            )
            uiState.areas.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { area ->
                        AreaTile(area, onClick = onNotYetAvailable, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionHeader(stringResource(R.string.vault_section_tools), modifier = Modifier.padding(top = 4.dp))
            ListRow(
                title = stringResource(R.string.vault_shamir_title),
                subtitle = stringResource(R.string.vault_shamir_subtitle),
                icon = MaterialSymbols.CallSplit,
                trailing = {
                    PillButton(
                        text = stringResource(R.string.vault_shamir_action),
                        onClick = onNotYetAvailable,
                        trailingIcon = MaterialSymbols.ArrowForward,
                        contentColor = colors.textPrimary,
                    )
                },
                onClick = onNotYetAvailable,
            )
            ListRow(
                title = stringResource(R.string.vault_backup_title),
                subtitle = stringResource(R.string.vault_backup_subtitle, uiState.lastBackup),
                icon = MaterialSymbols.Backup,
                trailing = { Tag(stringResource(R.string.vault_backup_tag), color = colors.mesh) },
                onClick = onNotYetAvailable,
            )
            DestructiveButton(
                text = stringResource(R.string.vault_lock_now),
                onClick = onNotYetAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                icon = MaterialSymbols.PowerSettingsNew,
            )
        }
    }
}

@Composable
private fun SessionCard(autoLockIn: String, onLock: () -> Unit) {
    val colors = TflTheme.colors
    TflCard(contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(MaterialSymbols.LockClock)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.vault_session_label), style = TflTheme.typography.labelSm, color = colors.textMuted)
                Text(
                    stringResource(R.string.vault_session_title, autoLockIn),
                    style = TflTheme.typography.labelLg.copy(fontSize = 16.sp),
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(R.string.vault_session_caption),
                    style = TflTheme.typography.bodySm,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DestructiveButton(
                text = stringResource(R.string.vault_lock),
                onClick = onLock,
                icon = MaterialSymbols.Lock,
                size = TflButtonSize.Medium,
            )
        }
    }
}

@Composable
private fun StorageCard(uiState: VaultUiState) {
    val colors = TflTheme.colors
    TflCard(verticalSpacing = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TflIcon(MaterialSymbols.PieChart, contentDescription = null, size = 20.dp, tint = colors.primary)
            Text(stringResource(R.string.vault_storage), style = TflTheme.typography.headlineSm, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.vault_storage_used, gigabytes(uiState.usedGigabytes)), style = TflTheme.typography.codeMd, color = colors.primary)
            Text(stringResource(R.string.vault_storage_total, gigabytes(uiState.totalGigabytes)), style = TflTheme.typography.codeMd, color = colors.textMuted)
        }
        SegmentedBar(
            segments = uiState.slices.map { BarSegment(it.gigabytes, colors.accent(it.accent)) } +
                BarSegment(uiState.totalGigabytes - uiState.usedGigabytes, Color.Transparent),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.slices.forEach { slice -> LegendItem(slice) }
        }
    }
}

@Composable
private fun LegendItem(slice: StorageSlice, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(colors.accent(slice.accent), CircleShape))
        Text(
            text = slice.label,
            modifier = Modifier.weight(1f),
            style = TflTheme.typography.bodySm,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(slice.sizeLabel, style = TflTheme.typography.codeSm, color = colors.textMuted)
    }
}

@Composable
private fun AreaTile(area: VaultArea, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val accent = colors.accent(area.accent)
    TflCard(onClick = onClick, modifier = modifier.height(150.dp), contentPadding = PaddingValues(14.dp), verticalSpacing = 2.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(area.icon, tint = accent, containerColor = accent.copy(alpha = 0.12f))
            Spacer(Modifier.weight(1f))
            when {
                area.tag != null -> Tag(area.tag, color = accent)
                area.locked -> TflIcon(MaterialSymbols.Lock, contentDescription = stringResource(R.string.vault_locked), size = 16.dp, tint = colors.textMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(area.title, style = TflTheme.typography.headlineSm.copy(fontSize = 16.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(area.summary, style = TflTheme.typography.bodySm, color = if (area.locked) colors.danger else colors.textMuted, maxLines = 1)
        if (area.detail != null) {
            Text(area.detail, style = TflTheme.typography.codeSm, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun TflColors.accent(accent: VaultAccent): Color = when (accent) {
    VaultAccent.PRIMARY -> primary
    VaultAccent.MESH -> mesh
    VaultAccent.TOR -> tor
    VaultAccent.SUCCESS -> success
    VaultAccent.DANGER -> danger
}

/** "24.8" or "128": one decimal only when it isn't a whole number. */
internal fun gigabytes(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
