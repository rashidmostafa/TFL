package app.tfl.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.CountBadge
import app.tfl.core.designsystem.component.DeliveryStatusIcon
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.ProfileButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SheetBody
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflLogo
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflSearchField
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.TransportChip
import app.tfl.core.designsystem.component.glow
import app.tfl.core.designsystem.component.initialsOf
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport

/**
 * @param onScan the SCAN button: add a friend by scanning their code.
 * @param onSetUpNearby Nearby is on but needs a permission or a switch.
 */
@Composable
internal fun ChatsScreen(
    onOpenProfile: () -> Unit,
    onScan: () -> Unit,
    onOpenChat: (contactId: Long) -> Unit,
    onResolveKey: (contactId: Long) -> Unit,
    onSetUpNearby: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatsContent(
        uiState = uiState,
        searchQuery = viewModel.searchQuery,
        onOpenProfile = onOpenProfile,
        onScan = onScan,
        onOpenChat = onOpenChat,
        onResolveKey = onResolveKey,
        onSetUpNearby = onSetUpNearby,
        modifier = modifier,
    )
}

@Composable
internal fun ChatsContent(
    uiState: ChatsUiState,
    searchQuery: TextFieldState,
    onOpenProfile: () -> Unit,
    onScan: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onResolveKey: (Long) -> Unit,
    onSetUpNearby: () -> Unit,
    modifier: Modifier = Modifier,
    startWithNewChat: Boolean = false,
) {
    var picking by remember { mutableStateOf(startWithNewChat) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            TflTopBar(
                title = stringResource(R.string.chats_title),
                subtitle = { NearbyStatusLine(uiState.nearby) },
                navigationIcon = { TflLogo() },
                actions = {
                    PillButton(
                        text = stringResource(R.string.chats_scan),
                        onClick = onScan,
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
                if (uiState.nearby is NearbyState.NeedsSetup) {
                    item(key = "setup") { NearbySetupPrompt(uiState.nearby.permitted, onSetUpNearby) }
                }
                if (uiState.threads.isNotEmpty() || uiState.hasQuery) {
                    item(key = "search") {
                        TflSearchField(searchQuery, placeholder = stringResource(R.string.chats_search_placeholder))
                    }
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
                    items(uiState.threads, key = { it.contactId }) { thread ->
                        ThreadCard(
                            thread = thread,
                            time = uiState.time,
                            onClick = { onOpenChat(thread.contactId) },
                            onResolveKey = { onResolveKey(thread.contactId) },
                        )
                    }
                }
                if (uiState.threads.isEmpty()) {
                    item(key = "empty") { ChatsEmpty(uiState, onScan = onScan, onNewChat = { picking = true }) }
                }
            }
        }
        if (uiState.hasFriends) {
            NewChatButton(
                onClick = { picking = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
    if (picking) {
        NewChatSheet(
            friends = uiState.friends,
            onPick = {
                picking = false
                onOpenChat(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun NearbyStatusLine(nearby: NearbyState) {
    val colors = TflTheme.colors
    when (nearby) {
        NearbyState.Off -> StatusLine(stringResource(R.string.chats_status_nearby_off), color = colors.textMuted)
        is NearbyState.NeedsSetup -> StatusLine(stringResource(R.string.chats_status_nearby_setup), color = colors.warning)
        is NearbyState.On -> StatusLine(pluralStringResource(R.plurals.chats_status_friends_nearby, nearby.friendsNearby, nearby.friendsNearby))
    }
}

/** Why Nearby can't look yet: a permission to give, or Bluetooth (or Location) switched off. */
@Composable
private fun NearbySetupPrompt(permitted: Boolean, onSetUp: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Callout(
            title = stringResource(R.string.chats_setup_title),
            text = stringResource(if (permitted) R.string.chats_setup_body_switch else R.string.chats_setup_body),
            tone = CalloutTone.Warning,
        )
        PillButton(
            text = stringResource(R.string.chats_setup_action),
            onClick = onSetUp,
            icon = MaterialSymbols.Sensors,
            contentColor = TflTheme.colors.warning,
        )
    }
}

@Composable
private fun ChatsEmpty(uiState: ChatsUiState, onScan: () -> Unit, onNewChat: () -> Unit) {
    when {
        uiState.hasQuery -> EmptyState(
            icon = MaterialSymbols.SearchOff,
            title = stringResource(R.string.chats_empty_search_title),
            message = stringResource(R.string.chats_empty_search_message),
        )
        !uiState.hasFriends -> EmptyState(
            icon = MaterialSymbols.PersonAdd,
            title = stringResource(R.string.chats_empty_no_friends_title),
            message = stringResource(R.string.chats_empty_no_friends_message),
            action = { PrimaryButton(stringResource(R.string.chats_scan_friend), onClick = onScan, icon = MaterialSymbols.QrCodeScanner, size = TflButtonSize.Medium) },
        )
        else -> EmptyState(
            icon = MaterialSymbols.Forum,
            title = stringResource(R.string.chats_empty_title),
            message = stringResource(R.string.chats_empty_message),
            action = { PrimaryButton(stringResource(R.string.chats_new_chat), onClick = onNewChat, icon = MaterialSymbols.EditSquare, size = TflButtonSize.Medium) },
        )
    }
}

private val AvatarColumn = 44.dp

/** A conversation: avatar, name, the last message, and how the friend can be reached. */
@Composable
private fun ThreadCard(thread: ThreadItem, time: TimeContext, onClick: () -> Unit, onResolveKey: () -> Unit) {
    val colors = TflTheme.colors
    val keyChanged = thread.trust == ContactTrust.KEY_CHANGED
    TflCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 10.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TflAvatar(
                initials = initialsOf(thread.name),
                badge = when {
                    keyChanged -> AvatarBadge.Alert
                    thread.nearbyNow -> AvatarBadge.Reachable(Transport.NEARBY)
                    else -> null
                },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = thread.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TflTheme.typography.headlineSm.copy(fontSize = 15.sp, lineHeight = 20.sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TrustIcon(thread.trust)
                }
                Text(
                    text = previewText(thread.preview),
                    style = TflTheme.typography.bodyMd,
                    color = when {
                        keyChanged -> colors.danger
                        thread.preview !is ThreadPreview.Text -> colors.textMuted
                        else -> colors.textPrimary.copy(alpha = 0.9f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                thread.atMillis?.let { Text(time.relative(it), style = TflTheme.typography.codeSm, color = colors.textMuted) }
                when {
                    keyChanged -> Tag(stringResource(R.string.chats_alert), color = colors.danger, solid = true)
                    thread.blocked -> Tag(stringResource(R.string.chats_blocked), color = colors.textMuted)
                    thread.unread > 0 -> CountBadge(thread.unread)
                    thread.lastStatus != null -> DeliveryStatusIcon(thread.lastStatus)
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(start = AvatarColumn + 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                // Nothing goes to them until the key is checked: the button says what to do.
                keyChanged -> Unit
                thread.nearbyNow -> TransportChip(Transport.NEARBY, detail = stringResource(R.string.chats_in_range))
                thread.lastStatus == DeliveryStatus.QUEUED -> TransportChip(Transport.QUEUED, detail = stringResource(R.string.chats_waiting))
                else -> TransportChip(Transport.NEARBY, detail = stringResource(R.string.chats_out_of_range))
            }
            Spacer(Modifier.weight(1f))
            if (keyChanged) {
                PillButton(
                    text = stringResource(R.string.chats_resolve_key),
                    onClick = onResolveKey,
                    trailingIcon = MaterialSymbols.ChevronRight,
                    contentColor = colors.danger,
                    containerColor = colors.danger.copy(alpha = 0.12f),
                )
            }
        }
    }
}

@Composable
private fun previewText(preview: ThreadPreview): String = when (preview) {
    is ThreadPreview.Text -> if (preview.mine) stringResource(R.string.chats_preview_mine, preview.text) else preview.text
    is ThreadPreview.Deleted -> stringResource(R.string.chats_message_deleted)
    is ThreadPreview.Timer -> stringResource(
        if (preview.seconds == 0) R.string.chats_preview_timer_off else R.string.chats_preview_timer,
        timerLabel(preview.seconds),
    )
    ThreadPreview.Empty -> stringResource(R.string.chats_preview_empty)
}

@Composable
internal fun TrustIcon(trust: ContactTrust) {
    val colors = TflTheme.colors
    when (trust) {
        ContactTrust.VERIFIED -> TflIcon(MaterialSymbols.VerifiedUser, contentDescription = stringResource(R.string.chats_trust_verified), size = 16.dp, tint = colors.primary)
        ContactTrust.UNVERIFIED -> TflIcon(MaterialSymbols.GppMaybe, contentDescription = stringResource(R.string.chats_trust_unverified), size = 16.dp, tint = colors.warning)
        ContactTrust.KEY_CHANGED -> TflIcon(MaterialSymbols.Warning, contentDescription = stringResource(R.string.chats_trust_key_changed), size = 16.dp, tint = colors.danger)
    }
}

@Composable
private fun NewChatSheet(friends: List<FriendItem>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.chats_new_chat), icon = MaterialSymbols.EditSquare, onClose = onDismiss)
        SheetBody(spacing = 8.dp) {
            friends.forEach { friend ->
                ListRow(
                    title = friend.name,
                    subtitle = stringResource(
                        when {
                            friend.blocked -> R.string.chats_blocked
                            friend.trust == ContactTrust.KEY_CHANGED -> R.string.chats_trust_key_changed
                            friend.trust == ContactTrust.VERIFIED -> R.string.chats_trust_verified
                            else -> R.string.chats_trust_unverified
                        },
                    ),
                    icon = MaterialSymbols.Person,
                    onClick = { onPick(friend.contactId) },
                )
            }
        }
    }
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
