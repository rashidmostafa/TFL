package app.tfl.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.ProfileButton
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.TflSearchField
import app.tfl.core.designsystem.component.TflSheetDefaults
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.map.components.FriendCard

private val SheetPeekHeight = 380.dp

@Composable
internal fun MapScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MapContent(uiState, viewModel.searchQuery, onOpenProfile, onNotYetAvailable, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapContent(
    uiState: MapUiState,
    searchQuery: TextFieldState,
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
            title = stringResource(R.string.map_title),
            subtitle = {
                StatusLine(
                    pluralStringResource(R.plurals.map_status_peers, uiState.peerCount, uiState.peerCount),
                    color = colors.mesh,
                )
            },
            actions = {
                TflIconButton(MaterialSymbols.Radar, stringResource(R.string.map_radar), onClick = onNotYetAvailable)
                ProfileButton(onClick = onOpenProfile)
            },
        )
        BottomSheetScaffold(
            sheetContent = { FriendsSheet(uiState, searchQuery, onNotYetAvailable) },
            modifier = Modifier.weight(1f),
            scaffoldState = rememberBottomSheetScaffoldState(),
            sheetPeekHeight = SheetPeekHeight,
            sheetShape = TflTheme.shapes.sheet,
            sheetContainerColor = colors.surface,
            sheetContentColor = colors.textPrimary,
            sheetTonalElevation = 0.dp,
            sheetShadowElevation = 0.dp,
            sheetDragHandle = { TflSheetDefaults.DragHandle() },
            containerColor = colors.canvas,
            contentColor = colors.textPrimary,
        ) {
            MapArea(uiState, onNotYetAvailable)
        }
    }
}

/** Stand-in for the offline map (osmdroid, Phase 8): a drawn street grid with friend markers. */
@Composable
private fun MapArea(uiState: MapUiState, onNotYetAvailable: () -> Unit) {
    val colors = TflTheme.colors
    Box(Modifier.fillMaxSize()) {
        PlaceholderMap(Modifier.fillMaxSize())
        // Markers live in the part of the map the peeking sheet leaves visible.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, top = 64.dp, end = 72.dp, bottom = SheetPeekHeight + 56.dp),
        ) {
            uiState.friends.forEach { friend ->
                FriendMarker(
                    friend = friend,
                    modifier = Modifier.align(BiasAlignment(friend.mapX * 2 - 1, friend.mapY * 2 - 1)),
                )
            }
        }
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .background(colors.surface.copy(alpha = 0.92f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
                Text(uiState.coordinates, style = TflTheme.typography.codeSm, color = colors.textPrimary, maxLines = 1)
            }
            Tag(stringResource(R.string.map_offline_preview), color = colors.textMuted)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TflIconButton(MaterialSymbols.Layers, stringResource(R.string.map_layers), onClick = onNotYetAvailable, containerColor = colors.surface)
            TflIconButton(MaterialSymbols.MyLocation, stringResource(R.string.map_center), onClick = onNotYetAvailable, containerColor = colors.surface)
        }
        PillButton(
            text = stringResource(R.string.map_drop_pin),
            onClick = onNotYetAvailable,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = SheetPeekHeight + 12.dp),
            icon = MaterialSymbols.AddLocationAlt,
            containerColor = colors.surface,
        )
    }
}

@Composable
private fun PlaceholderMap(modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    Canvas(modifier) {
        drawRect(Color(0xFF0E141B))
        val grid = 36.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += grid
        }
        var y = 0f
        while (y < size.height) {
            drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += grid
        }
        val top = size.height - SheetPeekHeight.toPx()
        val river = Path().apply {
            moveTo(0f, top * 0.78f)
            cubicTo(size.width * 0.3f, top * 0.62f, size.width * 0.6f, top * 0.98f, size.width, top * 0.8f)
        }
        drawPath(river, Color(0xFF0F2130), style = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round))
        val road = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(size.width * 0.05f, 0f)
                cubicTo(size.width * 0.3f, top * 0.3f, size.width * 0.25f, top * 0.7f, size.width * 0.45f, size.height)
            },
            colors.surfaceContainer,
            style = road,
        )
        drawPath(
            Path().apply {
                moveTo(0f, top * 0.35f)
                cubicTo(size.width * 0.4f, top * 0.42f, size.width * 0.7f, top * 0.25f, size.width, top * 0.4f)
            },
            colors.surfaceContainer,
            style = road,
        )
        drawLine(colors.surfaceContainer, Offset(size.width * 0.82f, 0f), Offset(size.width * 0.6f, size.height), strokeWidth = 4.dp.toPx())

        val me = Offset(size.width * 0.46f, top * 0.5f)
        drawCircle(colors.primary.copy(alpha = 0.07f), radius = 96.dp.toPx(), center = me)
        drawCircle(colors.primary.copy(alpha = 0.3f), radius = 96.dp.toPx(), center = me, style = Stroke(1.dp.toPx()))
        drawCircle(colors.primary.copy(alpha = 0.14f), radius = 40.dp.toPx(), center = me)
        drawCircle(colors.canvas, radius = 10.dp.toPx(), center = me)
        drawCircle(colors.primary, radius = 7.dp.toPx(), center = me)
    }
}

@Composable
private fun FriendMarker(friend: FriendLocation, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TflAvatar(friend.initials, size = 40.dp, badge = AvatarBadge.Reachable(friend.transport))
        Text(
            text = "${friend.shortName} · ${friend.markerLabel}",
            modifier = Modifier
                .background(colors.surface.copy(alpha = 0.92f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = TflTheme.typography.labelSm,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun FriendsSheet(
    uiState: MapUiState,
    searchQuery: TextFieldState,
    onNotYetAvailable: () -> Unit,
) {
    val colors = TflTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.map_active_friends), style = TflTheme.typography.headlineMd, color = colors.textPrimary)
                    Text(
                        pluralStringResource(R.plurals.map_sharing, uiState.sharingCount, uiState.sharingCount),
                        style = TflTheme.typography.codeSm,
                        color = colors.primary,
                    )
                }
                PillButton(
                    text = stringResource(R.string.map_sort_nearest),
                    onClick = onNotYetAvailable,
                    icon = MaterialSymbols.FilterAlt,
                    contentColor = colors.textPrimary,
                )
            }
        }
        item(key = "search") { TflSearchField(searchQuery, placeholder = stringResource(R.string.map_search_placeholder)) }
        items(uiState.friends, key = { it.id }) { FriendCard(it, onNavigate = onNotYetAvailable) }
        if (uiState.friends.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = MaterialSymbols.SearchOff,
                    title = stringResource(R.string.map_empty_title),
                    message = stringResource(R.string.map_empty_message),
                )
            }
        }
        item(key = "share") {
            PrimaryButton(
                text = stringResource(R.string.map_share_location),
                onClick = onNotYetAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                icon = MaterialSymbols.ShareLocation,
                glow = true,
            )
        }
        item(key = "caption") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TflIcon(MaterialSymbols.Lock, contentDescription = null, size = 14.dp, tint = colors.textMuted)
                Text(
                    text = stringResource(R.string.map_share_caption),
                    modifier = Modifier.padding(start = 6.dp),
                    style = TflTheme.typography.codeSm,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
