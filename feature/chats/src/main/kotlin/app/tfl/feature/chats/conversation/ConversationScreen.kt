package app.tfl.feature.chats.conversation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.CountBadge
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.TflSnackbarHost
import app.tfl.core.designsystem.component.initialsOf
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.Transport
import app.tfl.core.transport.SendRefusal
import app.tfl.feature.chats.ContactTrust
import app.tfl.feature.chats.R
import app.tfl.feature.chats.timerLabel

/** What the conversation screen can do besides its view model. */
internal class ConversationNavigation(
    val onBack: () -> Unit,
    val onOpenProfile: (contactId: Long) -> Unit,
    val onVerify: (contactId: Long) -> Unit,
    val onOpenScheduled: (contactId: Long) -> Unit,
)

@Composable
internal fun ConversationScreen(
    navigation: ConversationNavigation,
    modifier: Modifier = Modifier,
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var choosingTimer by rememberSaveable { mutableStateOf(false) }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onHidden() }
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { notice -> snackbar.showSnackbar(resources.noticeText(notice, uiState.name)) }
    }

    ConversationContent(
        uiState = uiState,
        composer = viewModel.composer,
        actions = ConversationActions(
            onBack = navigation.onBack,
            onOpenProfile = { navigation.onOpenProfile(uiState.contactId) },
            onVerify = { navigation.onVerify(uiState.contactId) },
            onOpenScheduled = { navigation.onOpenScheduled(uiState.contactId) },
            onChooseTimer = { choosingTimer = true },
            onSelect = viewModel::select,
            onSend = viewModel::send,
            onSchedule = {
                viewModel.refreshTime()
                scheduling = true
            },
            onCancelMode = viewModel::cancelComposerMode,
            onUnblock = viewModel::unblock,
        ),
        snackbar = snackbar,
        modifier = modifier,
    )

    uiState.actions?.let { actions ->
        MessageActionsSheet(
            actions = actions,
            onReact = { emoji -> viewModel.react(actions.message.id, emoji) },
            onReply = { viewModel.reply(actions.message.id) },
            onEdit = { viewModel.startEdit(actions.message.id) },
            onCopy = {
                actions.message.text?.let { context.copySensitive(it) }
                viewModel.copied()
            },
            onInfo = { viewModel.showInfo(actions.message.id) },
            onDeleteForMe = { viewModel.deleteForMe(actions.message.id) },
            onDeleteForEveryone = {
                viewModel.select(null)
                confirmingDelete = actions.message.id
            },
            onDismiss = { viewModel.select(null) },
        )
    }
    uiState.info?.let { info -> MessageInfoSheet(info, uiState.name, uiState.time, onDismiss = { viewModel.showInfo(null) }) }
    if (choosingTimer) {
        TimerSheet(
            current = uiState.timerSeconds,
            name = uiState.name,
            onSet = {
                choosingTimer = false
                viewModel.setTimer(it)
            },
            onDismiss = { choosingTimer = false },
        )
    }
    confirmingDelete?.let { id ->
        DeleteForEveryoneSheet(
            name = uiState.name,
            onDelete = {
                confirmingDelete = null
                viewModel.deleteForEveryone(id)
            },
            onDismiss = { confirmingDelete = null },
        )
    }
    if (scheduling) {
        ScheduleDialog(
            time = uiState.time,
            onPick = { at ->
                scheduling = false
                viewModel.schedule(at)
            },
            onDismiss = { scheduling = false },
        )
    }
}

internal class ConversationActions(
    val onBack: () -> Unit = {},
    val onOpenProfile: () -> Unit = {},
    val onVerify: () -> Unit = {},
    val onOpenScheduled: () -> Unit = {},
    val onChooseTimer: () -> Unit = {},
    val onSelect: (Long) -> Unit = {},
    val onSend: () -> Unit = {},
    val onSchedule: () -> Unit = {},
    val onCancelMode: () -> Unit = {},
    val onUnblock: () -> Unit = {},
)

@Composable
internal fun ConversationContent(
    uiState: ConversationUiState,
    composer: TextFieldState,
    actions: ConversationActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = TflTheme.colors
    val listState = rememberLazyListState()
    KeepNewestInView(listState, uiState.rows)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            ConversationTopBar(uiState, actions)
            EncryptionBanner(uiState.trust, uiState.fingerprint)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                // Newest at the bottom, where the list starts.
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.rows.asReversed(), key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.Day -> DaySeparator(uiState.time, row.atMillis)
                        is ChatRow.TimerChanged -> TimerChangedLine(row, uiState.name)
                        is ChatRow.Message -> MessageRow(row.message, uiState.name, uiState.time, onLongPress = { actions.onSelect(row.message.id) })
                    }
                }
                if (uiState.rows.isEmpty() && !uiState.loading) {
                    item(key = "start") { ConversationStart(uiState.name) }
                }
            }
            Composer(uiState, composer, actions)
        }
        TflSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * Shows a new message as it arrives if the newest one was in view, and always your own. Left alone,
 * the list keeps the row that was at the bottom where it was, and new rows land out of sight below.
 */
@Composable
private fun KeepNewestInView(state: LazyListState, rows: List<ChatRow>) {
    val newest = rows.lastOrNull()
    val shown = remember { NewestRow() }
    // Runs before the new rows are measured, so the list still tells where it was.
    SideEffect {
        val key = newest?.key
        if (key == shown.key) return@SideEffect
        val wasAtNewest = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        val mine = (newest as? ChatRow.Message)?.message?.outgoing == true
        if (shown.key != null && (wasAtNewest || mine)) state.requestScrollToItem(0)
        shown.key = key
    }
}

/** The newest row the list has shown; not state, as nothing is drawn from it. */
private class NewestRow {
    var key: String? = null
}

@Composable
private fun ConversationTopBar(uiState: ConversationUiState, actions: ConversationActions) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TflBackButton(onClick = actions.onBack)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.chat_open_profile), onClick = actions.onOpenProfile),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TflAvatar(
                initials = initialsOf(uiState.name),
                size = 40.dp,
                badge = when {
                    uiState.trust == ContactTrust.KEY_CHANGED -> AvatarBadge.Alert
                    uiState.nearbyNow -> AvatarBadge.Reachable(Transport.NEARBY)
                    else -> null
                },
            )
            Column(Modifier.weight(1f)) {
                Text(uiState.name, style = TflTheme.typography.headlineSm, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(if (uiState.nearbyNow) R.string.chat_nearby_now else R.string.chat_not_in_range),
                        style = TflTheme.typography.codeSm,
                        color = if (uiState.nearbyNow) colors.primary else colors.textMuted,
                    )
                    if (uiState.timerSeconds > 0) {
                        TflIcon(MaterialSymbols.Timer, contentDescription = null, size = 14.dp, tint = colors.primary)
                        Text(timerLabel(uiState.timerSeconds), style = TflTheme.typography.codeSm, color = colors.primary)
                    }
                }
            }
        }
        TflIconButton(
            symbol = if (uiState.timerSeconds > 0) MaterialSymbols.Timer else MaterialSymbols.TimerOff,
            contentDescription = stringResource(R.string.chat_disappearing),
            onClick = actions.onChooseTimer,
            tint = if (uiState.timerSeconds > 0) colors.primary else colors.textMuted,
        )
        Box {
            TflIconButton(
                symbol = MaterialSymbols.ScheduleSend,
                contentDescription = pluralStringResource(R.plurals.chat_scheduled_count, uiState.scheduledCount, uiState.scheduledCount),
                onClick = actions.onOpenScheduled,
            )
            if (uiState.scheduledCount > 0) CountBadge(uiState.scheduledCount, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** What protects this conversation, stated as it is: no claims the code doesn't make good. */
@Composable
private fun EncryptionBanner(trust: ContactTrust, fingerprint: List<String>) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .background(colors.surface, TflTheme.shapes.control)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TflIcon(MaterialSymbols.Lock, contentDescription = null, size = 18.dp, tint = colors.primary)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.chat_encrypted), style = TflTheme.typography.labelMd, color = colors.textPrimary)
            if (fingerprint.isNotEmpty()) {
                Text(
                    text = "${fingerprint.first()} ··· ${fingerprint.last()}",
                    style = TflTheme.typography.codeSm,
                    color = colors.textMuted,
                    maxLines = 1,
                )
            }
        }
        when (trust) {
            ContactTrust.VERIFIED -> Tag(stringResource(R.string.chat_trust_verified), color = colors.primary)
            ContactTrust.UNVERIFIED -> Tag(stringResource(R.string.chat_trust_unverified), color = colors.warning)
            ContactTrust.KEY_CHANGED -> Tag(stringResource(R.string.chat_trust_key_changed), color = colors.danger)
        }
    }
}

@Composable
private fun ConversationStart(name: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TflIcon(MaterialSymbols.Lock, contentDescription = null, size = 28.dp, tint = TflTheme.colors.primary)
        Text(
            text = stringResource(R.string.chat_start, name),
            style = TflTheme.typography.bodyMd,
            color = TflTheme.colors.textMuted,
        )
    }
}

private fun Resources.noticeText(notice: ChatNotice, name: String): String = when (notice) {
    ChatNotice.Copied -> getString(R.string.chat_copied)
    ChatNotice.Scheduled -> getString(R.string.chat_scheduled_done)
    is ChatNotice.Refused -> when (notice.reason) {
        SendRefusal.BLOCKED -> getString(R.string.chat_refused_blocked, name)
        SendRefusal.KEY_CHANGED -> getString(R.string.chat_refused_key_changed, name)
        SendRefusal.NOT_FOUND -> getString(R.string.chat_refused_not_found)
        SendRefusal.INVALID -> getString(R.string.chat_refused_invalid)
        SendRefusal.NOT_ALLOWED -> getString(R.string.chat_refused_not_allowed)
        SendRefusal.LOCKED -> getString(R.string.chat_refused_locked)
    }
}

/** Copies [text] marked sensitive: Android 13+ hides it from the clipboard preview. */
private fun Context.copySensitive(text: String) {
    val clip = ClipData.newPlainText("", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/** ClipDescription.EXTRA_IS_SENSITIVE, which older Android ignores. */
private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
