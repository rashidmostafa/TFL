package app.tfl.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.ProfileButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflLogo
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflColors
import app.tfl.core.designsystem.theme.TflTheme

@Composable
internal fun ToolsScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ToolsContent(uiState, onOpenProfile, onNotYetAvailable, modifier)
}

@Composable
internal fun ToolsContent(
    uiState: ToolsUiState,
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
            title = stringResource(R.string.tools_title),
            subtitle = {
                StatusLine(
                    stringResource(
                        R.string.tools_status,
                        pluralStringResource(R.plurals.tools_status_peers, uiState.peerCount, uiState.peerCount),
                        pluralStringResource(R.plurals.tools_status_docs, uiState.syncedDocuments, uiState.syncedDocuments),
                    ),
                )
            },
            navigationIcon = { TflLogo() },
            actions = { ProfileButton(onClick = onOpenProfile) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroCard(uiState.collaborators)
            SectionHeader(
                title = stringResource(R.string.tools_section_tools),
                modifier = Modifier.padding(top = 4.dp),
                trailing = pluralStringResource(R.plurals.tools_section_tools_count, uiState.tools.size, uiState.tools.size),
                trailingColor = colors.primary,
            )
            uiState.tools.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { tool -> ToolTile(tool, onClick = onNotYetAvailable, modifier = Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SyncStrip(uiState.syncingDocuments)
            SectionHeader(
                title = stringResource(R.string.tools_section_activity),
                modifier = Modifier.padding(top = 4.dp),
                trailing = stringResource(R.string.tools_view_all),
                trailingColor = colors.primary,
                onTrailingClick = onNotYetAvailable,
            )
            uiState.activity.forEach { ActivityRow(it, onClick = onNotYetAvailable) }
            PrimaryButton(
                text = stringResource(R.string.tools_new_item),
                onClick = onNotYetAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                icon = MaterialSymbols.AddCircle,
            )
        }
    }
}

@Composable
private fun HeroCard(collaborators: List<String>) {
    val colors = TflTheme.colors
    TflCard(borderColor = colors.primary.copy(alpha = 0.25f), verticalSpacing = 10.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(stringResource(R.string.tools_tag_local))
            Tag(stringResource(R.string.tools_tag_servers), color = colors.textMuted)
        }
        Text(stringResource(R.string.tools_hero_title), style = TflTheme.typography.headlineMd, color = colors.textPrimary)
        Text(stringResource(R.string.tools_hero_body), style = TflTheme.typography.bodyMd, color = colors.textMuted)
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = (28 + (collaborators.size - 1).coerceAtLeast(0) * 20).dp, height = 28.dp)) {
                collaborators.forEachIndexed { index, initials ->
                    TflAvatar(
                        initials = initials,
                        modifier = Modifier
                            .offset(x = (index * 20).dp)
                            .border(2.dp, colors.surface, CircleShape),
                        size = 28.dp,
                    )
                }
            }
            Text(
                text = pluralStringResource(R.plurals.tools_hero_ready, collaborators.size, collaborators.size),
                modifier = Modifier.padding(start = 10.dp),
                style = TflTheme.typography.codeSm,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun ToolTile(tool: ToolItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val accent = colors.accent(tool.accent)
    TflCard(onClick = onClick, modifier = modifier.height(178.dp), contentPadding = PaddingValues(14.dp), verticalSpacing = 4.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(tool.icon, tint = accent, containerColor = accent.copy(alpha = 0.12f))
            Spacer(Modifier.weight(1f))
            Tag(tool.badge, color = accent)
        }
        Spacer(Modifier.height(6.dp))
        Text(tool.title, style = TflTheme.typography.headlineSm.copy(fontSize = 15.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(tool.description, style = TflTheme.typography.bodySm, color = colors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Text(tool.footer, style = TflTheme.typography.codeSm, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SyncStrip(documents: Int) {
    val colors = TflTheme.colors
    TflCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), borderColor = colors.mesh.copy(alpha = 0.25f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(colors.mesh, CircleShape))
            Column(Modifier.weight(1f)) {
                Text(pluralStringResource(R.plurals.tools_syncing, documents, documents), style = TflTheme.typography.codeMd, color = colors.mesh)
                Text(stringResource(R.string.tools_syncing_route), style = TflTheme.typography.codeSm, color = colors.textMuted)
            }
            TflIcon(MaterialSymbols.Sensors, contentDescription = null, size = 20.dp, tint = colors.mesh)
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val colors = TflTheme.colors
    val accent = colors.accent(item.accent)
    TflCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(item.icon, tint = colors.textPrimary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = TflTheme.typography.labelLg.copy(fontSize = 15.sp), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.details, style = TflTheme.typography.bodySm, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(accent, CircleShape))
                Text(item.status, style = TflTheme.typography.labelSm, color = accent)
            }
        }
    }
}

internal fun TflColors.accent(accent: ToolAccent): Color = when (accent) {
    ToolAccent.PRIMARY -> primary
    ToolAccent.MESH -> mesh
    ToolAccent.TOR -> tor
    ToolAccent.SUCCESS -> success
}
