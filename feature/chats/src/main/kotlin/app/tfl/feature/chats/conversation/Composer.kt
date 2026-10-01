package app.tfl.feature.chats.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.StatusPill
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.message.MessageLimits
import app.tfl.feature.chats.R
import app.tfl.feature.chats.timerLabel

/** Where messages are written, or why none can be sent to this friend (and what to do about it). */
@Composable
internal fun Composer(uiState: ConversationUiState, composer: TextFieldState, actions: ConversationActions) {
    val colors = TflTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (uiState.block) {
            SendBlock.BLOCKED -> BlockNotice(
                text = stringResource(R.string.chat_blocked_notice, uiState.name),
                action = stringResource(R.string.chat_unblock),
                onAction = actions.onUnblock,
            )
            SendBlock.KEY_CHANGED -> BlockNotice(
                text = stringResource(R.string.chat_key_changed_notice, uiState.name),
                action = stringResource(R.string.chat_verify),
                onAction = actions.onVerify,
                danger = true,
            )
            SendBlock.GONE -> BlockNotice(text = stringResource(R.string.chat_gone_notice), action = null, onAction = {})
            null -> Writing(uiState, composer, actions)
        }
    }
}

@Composable
private fun BlockNotice(text: String, action: String?, onAction: () -> Unit, danger: Boolean = false) {
    val colors = TflTheme.colors
    val tint = if (danger) colors.danger else colors.warning
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        TflIcon(if (danger) MaterialSymbols.Warning else MaterialSymbols.Block, contentDescription = null, size = 22.dp, tint = tint)
        Text(text, modifier = Modifier.weight(1f), style = TflTheme.typography.bodyMd, color = colors.textPrimary)
        if (action != null) PillButton(action, onClick = onAction, contentColor = tint, containerColor = tint.copy(alpha = 0.12f))
    }
}

@Composable
private fun Writing(uiState: ConversationUiState, composer: TextFieldState, actions: ConversationActions) {
    val colors = TflTheme.colors
    val quote = uiState.replyingTo
    when {
        uiState.editing -> ModeBar(MaterialSymbols.Edit, stringResource(R.string.chat_editing), null, actions.onCancelMode)
        quote != null -> ModeBar(
            MaterialSymbols.Reply,
            stringResource(R.string.chat_replying_to, if (quote.fromMe) stringResource(R.string.chat_you) else uiState.name),
            quote.text ?: stringResource(R.string.chat_quote_missing),
            actions.onCancelMode,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.timerSeconds > 0) StatusPill(timerLabel(uiState.timerSeconds), icon = MaterialSymbols.Timer)
        if (uiState.nearbyNow) {
            StatusPill(stringResource(R.string.chat_nearby_now))
        } else {
            StatusPill(stringResource(R.string.chat_will_queue), color = colors.warning)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            state = composer,
            modifier = Modifier.weight(1f),
            inputTransformation = InputTransformation.maxLength(MessageLimits.MAX_TEXT_CHARS),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
            textStyle = TflTheme.typography.bodyLg.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.primary),
            decorator = { field ->
                Box(
                    modifier = Modifier
                        .background(colors.surfaceContainer, TflTheme.shapes.control)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (composer.text.isEmpty()) {
                        Text(stringResource(R.string.chat_placeholder), style = TflTheme.typography.bodyLg, color = colors.textDim)
                    }
                    field()
                }
            },
        )
        val canSend = composer.text.isNotBlank()
        if (!uiState.editing) {
            TflIconButton(
                symbol = MaterialSymbols.Schedule,
                contentDescription = stringResource(R.string.chat_schedule),
                onClick = actions.onSchedule,
                enabled = canSend,
            )
        }
        TflIconButton(
            symbol = if (uiState.editing) MaterialSymbols.Check else MaterialSymbols.ArrowUpward,
            contentDescription = stringResource(if (uiState.editing) R.string.chat_save_edit else R.string.chat_send),
            onClick = actions.onSend,
            enabled = canSend,
            filled = true,
            containerColor = colors.primary,
            tint = colors.onPrimary,
        )
    }
}

@Composable
private fun ModeBar(icon: String, title: String, detail: String?, onCancel: () -> Unit) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, TflTheme.shapes.control)
            .padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TflIcon(icon, contentDescription = null, size = 18.dp, tint = colors.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = TflTheme.typography.labelMd, color = colors.primary, maxLines = 1)
            if (detail != null) Text(detail, style = TflTheme.typography.bodySm, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TflIconButton(MaterialSymbols.Close, contentDescription = stringResource(R.string.chat_cancel), onClick = onCancel, containerColor = colors.surfaceContainer)
    }
}
