package app.tfl.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.ProfileButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.TflFilterChip
import app.tfl.core.designsystem.component.TflLogo
import app.tfl.core.designsystem.component.TflSearchField
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.glow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.chats.components.BroadcastCard
import app.tfl.feature.chats.components.ConversationCard

@Composable
internal fun ChatsScreen(
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatsContent(
        uiState = uiState,
        searchQuery = viewModel.searchQuery,
        onFilterSelect = viewModel::onFilterSelect,
        onOpenProfile = onOpenProfile,
        onNotYetAvailable = onNotYetAvailable,
        modifier = modifier,
    )
}

@Composable
internal fun ChatsContent(
    uiState: ChatsUiState,
    searchQuery: TextFieldState,
    onFilterSelect: (ChatFilter) -> Unit,
    onOpenProfile: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            TflTopBar(
                title = stringResource(R.string.chats_title),
                subtitle = { StatusLine(statusText(uiState)) },
                navigationIcon = { TflLogo() },
                actions = {
                    PillButton(
                        text = stringResource(R.string.chats_scan),
                        onClick = onNotYetAvailable,
                        icon = MaterialSymbols.QrCodeScanner,
                    )
                    ProfileButton(onClick = onOpenProfile)
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "search") {
                    TflSearchField(searchQuery, placeholder = stringResource(R.string.chats_search_placeholder))
                }
                item(key = "filters") { FilterRow(uiState, onFilterSelect) }
                if (uiState.broadcasts.isNotEmpty()) {
                    item(key = "pinned-header") {
                        SectionHeader(
                            title = stringResource(R.string.chats_section_pinned),
                            modifier = Modifier.padding(top = 8.dp),
                            icon = MaterialSymbols.PushPin,
                            trailing = stringResource(R.string.chats_section_pinned_trailing),
                            trailingColor = TflTheme.colors.primary,
                        )
                    }
                    items(uiState.broadcasts, key = { it.id }) { BroadcastCard(it, onClick = onNotYetAvailable) }
                }
                if (uiState.threads.isNotEmpty()) {
                    item(key = "threads-header") {
                        SectionHeader(
                            title = stringResource(R.string.chats_section_threads),
                            modifier = Modifier.padding(top = 8.dp),
                            icon = MaterialSymbols.Forum,
                            iconTint = TflTheme.colors.mesh,
                            trailing = stringResource(R.string.chats_section_threads_trailing),
                        )
                    }
                    items(uiState.threads, key = { it.id }) {
                        ConversationCard(it, onClick = onNotYetAvailable, onResolveKey = onNotYetAvailable)
                    }
                }
                if (uiState.isEmpty) {
                    item(key = "empty") {
                        EmptyState(
                            icon = if (uiState.hasQuery) MaterialSymbols.SearchOff else MaterialSymbols.Forum,
                            title = stringResource(
                                if (uiState.hasQuery) R.string.chats_empty_search_title else R.string.chats_empty_filter_title,
                            ),
                            message = stringResource(
                                if (uiState.hasQuery) R.string.chats_empty_search_message else R.string.chats_empty_filter_message,
                            ),
                        )
                    }
                }
            }
        }
        NewChatButton(
            onClick = onNotYetAvailable,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun statusText(uiState: ChatsUiState): String = stringResource(
    R.string.chats_status_line,
    pluralStringResource(R.plurals.chats_status_peers, uiState.peerCount, uiState.peerCount),
    stringResource(if (uiState.torConnected) R.string.chats_status_tor_on else R.string.chats_status_tor_off),
)

@Composable
private fun FilterRow(uiState: ChatsUiState, onFilterSelect: (ChatFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChatFilter.entries.forEach { filter ->
            TflFilterChip(
                label = stringResource(filter.label),
                selected = uiState.filter == filter,
                onClick = { onFilterSelect(filter) },
                icon = filter.icon,
                count = uiState.counts[filter],
            )
        }
    }
}

private val ChatFilter.label: Int
    get() = when (this) {
        ChatFilter.ALL -> R.string.chats_filter_all
        ChatFilter.DIRECT -> R.string.chats_filter_direct
        ChatFilter.GROUPS -> R.string.chats_filter_groups
        ChatFilter.BROADCASTS -> R.string.chats_filter_broadcasts
    }

private val ChatFilter.icon: String?
    get() = when (this) {
        ChatFilter.ALL -> null
        ChatFilter.DIRECT -> MaterialSymbols.Lock
        ChatFilter.GROUPS -> MaterialSymbols.Hub
        ChatFilter.BROADCASTS -> MaterialSymbols.CellTower
    }

@Composable
private fun NewChatButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .glow(colors.primary, shape),
        shape = shape,
        color = colors.primary,
        contentColor = colors.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TflIcon(MaterialSymbols.EditSquare, contentDescription = stringResource(R.string.chats_new_chat), size = 26.dp)
        }
    }
}
