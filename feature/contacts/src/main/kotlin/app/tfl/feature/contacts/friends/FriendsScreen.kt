package app.tfl.feature.contacts.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.TrustBadge

@Composable
internal fun FriendsScreen(
    onBack: () -> Unit,
    onAddFriend: () -> Unit,
    onOpenFriend: (FriendItem) -> Unit,
    onOpenGroupJoinPreview: () -> Unit,
    onOpenRemoteWipePreview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FriendsContent(uiState, onBack, onAddFriend, onOpenFriend, onOpenGroupJoinPreview, onOpenRemoteWipePreview, modifier)
}

@Composable
internal fun FriendsContent(
    uiState: FriendsUiState,
    onBack: () -> Unit,
    onAddFriend: () -> Unit,
    onOpenFriend: (FriendItem) -> Unit,
    onOpenGroupJoinPreview: () -> Unit,
    onOpenRemoteWipePreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.friends_title),
            subtitle = if (uiState.loading) {
                null
            } else {
                { StatusLine(pluralStringResource(R.plurals.friends_count, uiState.friends.size, uiState.friends.size)) }
            },
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.loading && uiState.friends.isEmpty()) {
                EmptyState(
                    icon = MaterialSymbols.Group,
                    title = stringResource(R.string.friends_empty_title),
                    message = stringResource(R.string.friends_empty_body),
                    action = {
                        PrimaryButton(
                            stringResource(R.string.friends_add),
                            onClick = onAddFriend,
                            icon = MaterialSymbols.PersonAdd,
                            size = TflButtonSize.Medium,
                        )
                    },
                )
            } else if (uiState.friends.isNotEmpty()) {
                PrimaryButton(
                    stringResource(R.string.friends_add),
                    onClick = onAddFriend,
                    icon = MaterialSymbols.PersonAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
                TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                    uiState.friends.forEachIndexed { index, friend ->
                        if (index > 0) TflDivider()
                        FriendRow(friend, onClick = { onOpenFriend(friend) })
                    }
                }
            }
            SectionHeader(
                stringResource(R.string.friends_previews),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Preview,
                iconTint = colors.warning,
            )
            ListRow(
                title = stringResource(R.string.group_join_title),
                subtitle = stringResource(R.string.friends_preview_subtitle),
                icon = MaterialSymbols.GroupAdd,
                titleBadge = { Tag(stringResource(R.string.preview_tag), color = colors.warning) },
                onClick = onOpenGroupJoinPreview,
            )
            ListRow(
                title = stringResource(R.string.remote_wipe_title),
                subtitle = stringResource(R.string.friends_preview_subtitle),
                icon = MaterialSymbols.PhonelinkLock,
                titleBadge = { Tag(stringResource(R.string.preview_tag), color = colors.warning) },
                onClick = onOpenRemoteWipePreview,
            )
        }
    }
}

@Composable
private fun FriendRow(friend: FriendItem, onClick: () -> Unit) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TflAvatar(friend.initials, badge = if (friend.trust == Trust.KEY_CHANGED) AvatarBadge.Alert else null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                friend.name,
                style = TflTheme.typography.bodyLg,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(friend.fingerprintStart, style = TflTheme.typography.codeSm, color = colors.textMuted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TrustBadge(friend.trust)
            if (friend.blocked) Tag(stringResource(R.string.friends_blocked), color = colors.danger)
        }
    }
}
