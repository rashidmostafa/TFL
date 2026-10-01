package app.tfl.feature.chats.scheduled

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SheetBody
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.StatusPill
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflSnackbarHost
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.message.MessageLimits
import app.tfl.core.transport.SendRefusal
import app.tfl.feature.chats.R
import app.tfl.feature.chats.TimeContext
import app.tfl.feature.chats.conversation.ScheduleDialog
import app.tfl.feature.chats.upcoming

@Composable
internal fun ScheduledScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    /** The message being changed; 0 for a new one, null when the editor is closed. */
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.refused.collect { reason ->
            snackbar.showSnackbar(
                resources.getString(
                    when (reason) {
                        SendRefusal.BLOCKED -> R.string.chat_refused_blocked
                        SendRefusal.KEY_CHANGED -> R.string.chat_refused_key_changed
                        SendRefusal.NOT_ALLOWED -> R.string.scheduled_refused_gone
                        else -> R.string.chat_refused_not_found
                    },
                    uiState.name,
                ),
            )
        }
    }
    ScheduledContent(
        uiState = uiState,
        onBack = onBack,
        onNew = {
            viewModel.refreshTime()
            editing = 0
        },
        onEdit = {
            viewModel.refreshTime()
            editing = it
        },
        onSendNow = viewModel::sendNow,
        onDelete = viewModel::delete,
        snackbar = snackbar,
        modifier = modifier,
    )
    editing?.let { id ->
        val item = uiState.items.firstOrNull { it.id == id }
        ScheduledEditor(
            time = uiState.time,
            initialText = item?.text.orEmpty(),
            initialAtMillis = item?.atMillis,
            onSave = { text, at ->
                editing = null
                if (item == null) viewModel.schedule(text, at) else viewModel.change(item.id, text, at)
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
internal fun ScheduledContent(
    uiState: ScheduledUiState,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Long) -> Unit,
    onSendNow: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            TflTopBar(
                title = stringResource(R.string.scheduled_title),
                subtitle = { Text(uiState.name, style = TflTheme.typography.codeSm, color = TflTheme.colors.textMuted) },
                navigationIcon = { TflBackButton(onClick = onBack) },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "about") {
                    Callout(title = stringResource(R.string.scheduled_about_title), text = stringResource(R.string.scheduled_about, uiState.name))
                }
                items(uiState.items, key = { it.id }) { item ->
                    ScheduledCard(item, uiState.time, onEdit = { onEdit(item.id) }, onSendNow = { onSendNow(item.id) }, onDelete = { onDelete(item.id) }, enabled = uiState.canSend)
                }
                if (uiState.items.isEmpty() && !uiState.loading) {
                    item(key = "empty") {
                        EmptyState(
                            icon = MaterialSymbols.ScheduleSend,
                            title = stringResource(R.string.scheduled_empty_title),
                            message = stringResource(R.string.scheduled_empty_message),
                        )
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.scheduled_new),
                onClick = onNew,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
                enabled = uiState.canSend,
                icon = MaterialSymbols.AddCircle,
            )
        }
        TflSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScheduledCard(item: ScheduledItem, time: TimeContext, onEdit: () -> Unit, onSendNow: () -> Unit, onDelete: () -> Unit, enabled: Boolean) {
    val colors = TflTheme.colors
    TflCard(modifier = Modifier.fillMaxWidth(), verticalSpacing = 12.dp) {
        StatusPill(time.upcoming(item.atMillis), icon = MaterialSymbols.Schedule)
        Text(
            text = item.text,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas, TflTheme.shapes.control)
                .padding(12.dp),
            style = TflTheme.typography.bodyLg,
            color = colors.textPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            TflIconButton(MaterialSymbols.Edit, contentDescription = stringResource(R.string.scheduled_change), onClick = onEdit, enabled = enabled)
            TflIconButton(MaterialSymbols.Send, contentDescription = stringResource(R.string.scheduled_send_now), onClick = onSendNow, enabled = enabled, tint = colors.primary)
            TflIconButton(MaterialSymbols.Delete, contentDescription = stringResource(R.string.scheduled_delete), onClick = onDelete, tint = colors.danger)
        }
    }
}

/** Writes or changes a scheduled message: its text, and when it goes. */
@Composable
private fun ScheduledEditor(
    time: TimeContext,
    initialText: String,
    initialAtMillis: Long?,
    onSave: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TflTheme.colors
    val text = rememberTextFieldState(initialText)
    var at by rememberSaveable { mutableLongStateOf(initialAtMillis ?: 0L) }
    var picking by rememberSaveable { mutableStateOf(false) }
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            stringResource(if (initialAtMillis == null) R.string.scheduled_new else R.string.scheduled_change),
            icon = MaterialSymbols.ScheduleSend,
            onClose = onDismiss,
        )
        SheetBody(Modifier.imePadding()) {
            BasicTextField(
                state = text,
                modifier = Modifier.fillMaxWidth(),
                inputTransformation = InputTransformation.maxLength(MessageLimits.MAX_TEXT_CHARS),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
                textStyle = TflTheme.typography.bodyLg.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.primary),
                decorator = { field ->
                    Box(
                        Modifier
                            .background(colors.surfaceContainer, TflTheme.shapes.control)
                            .heightIn(min = 96.dp)
                            .padding(16.dp),
                    ) {
                        if (text.text.isEmpty()) Text(stringResource(R.string.chat_placeholder), style = TflTheme.typography.bodyLg, color = colors.textDim)
                        field()
                    }
                },
            )
            ListRow(
                title = if (at > time.nowMillis) time.upcoming(at) else stringResource(R.string.scheduled_pick_time),
                icon = MaterialSymbols.Schedule,
                onClick = { picking = true },
            )
            PrimaryButton(
                text = stringResource(R.string.scheduled_save),
                onClick = { onSave(text.text.toString(), at) },
                modifier = Modifier.fillMaxWidth(),
                enabled = text.text.isNotBlank() && at > time.nowMillis,
            )
        }
    }
    if (picking) {
        ScheduleDialog(
            time = time,
            initialMillis = at.takeIf { it > time.nowMillis },
            onPick = {
                at = it
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
