package app.tfl.feature.chats.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.BubbleDirection
import app.tfl.core.designsystem.component.BubbleMeta
import app.tfl.core.designsystem.component.MessageBubble
import app.tfl.core.designsystem.component.TransportChip
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.feature.chats.R
import app.tfl.feature.chats.TimeContext
import app.tfl.feature.chats.clockTime
import app.tfl.feature.chats.dayLabel
import app.tfl.feature.chats.timerLabel

@Composable
internal fun DaySeparator(time: TimeContext, atMillis: Long) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = time.dayLabel(atMillis),
            modifier = Modifier
                .background(TflTheme.colors.surface, TflTheme.shapes.pill)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = TflTheme.typography.labelSm,
            color = TflTheme.colors.textMuted,
        )
    }
}

@Composable
internal fun TimerChangedLine(row: ChatRow.TimerChanged, name: String) {
    val text = when {
        row.seconds == 0 && row.mine -> stringResource(R.string.chat_timer_off_by_you)
        row.seconds == 0 -> stringResource(R.string.chat_timer_off_by_them, name)
        row.mine -> stringResource(R.string.chat_timer_set_by_you, timerLabel(row.seconds))
        else -> stringResource(R.string.chat_timer_set_by_them, name, timerLabel(row.seconds))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TflIcon(if (row.seconds == 0) MaterialSymbols.TimerOff else MaterialSymbols.Timer, contentDescription = null, size = 14.dp, tint = TflTheme.colors.textMuted)
        Text(text, modifier = Modifier.weight(1f, fill = false), style = TflTheme.typography.bodySm, color = TflTheme.colors.textMuted, textAlign = TextAlign.Center)
    }
}

/** A message bubble, on the right when it's yours; long-press (or the accessibility action) opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(message: MessageView, name: String, time: TimeContext, onLongPress: () -> Unit) {
    val colors = TflTheme.colors
    val direction = if (message.outgoing) BubbleDirection.Outgoing else BubbleDirection.Incoming
    val optionsLabel = stringResource(R.string.chat_message_options)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
    ) {
        MessageBubble(
            direction = direction,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = optionsLabel)
                .semantics { customActions = listOf(CustomAccessibilityAction(optionsLabel) { onLongPress(); true }) },
            footer = {
                FooterChip(message)
                Spacer(Modifier.weight(1f))
                if (message.disappearsAtMillis != null) {
                    TflIcon(MaterialSymbols.Timer, contentDescription = stringResource(R.string.chat_disappears), size = 14.dp, tint = colors.textMuted)
                }
                if (message.edited) Text(stringResource(R.string.chat_edited), style = TflTheme.typography.codeSm, color = colors.textDim, fontStyle = FontStyle.Italic)
                BubbleMeta(time.clockTime(message.atMillis), status = if (message.outgoing && !message.deleted) message.status else null)
            },
        ) {
            message.quote?.let { QuoteBlock(it, name) }
            if (message.deleted) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TflIcon(MaterialSymbols.Block, contentDescription = null, size = 16.dp, tint = colors.textMuted)
                    Text(stringResource(R.string.chats_message_deleted), style = TflTheme.typography.bodyMd, color = colors.textMuted, fontStyle = FontStyle.Italic)
                }
            } else {
                Text(message.text.orEmpty(), style = TflTheme.typography.bodyLg)
            }
        }
        if (message.reactions.isNotEmpty()) Reactions(message)
    }
}

/** How it travelled, once it did: Nearby for now. Waiting messages show their clock in the meta instead. */
@Composable
private fun FooterChip(message: MessageView) {
    when {
        message.deleted -> Unit
        message.outgoing && message.status == DeliveryStatus.QUEUED -> TransportChip(Transport.QUEUED)
        message.transport != null -> TransportChip(message.transport)
    }
}

@Composable
private fun QuoteBlock(quote: Quote, name: String) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.canvas.copy(alpha = 0.6f), TflTheme.shapes.keyBlock)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .background(if (quote.fromMe) colors.primary else colors.mesh, TflTheme.shapes.pill),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = if (quote.fromMe) stringResource(R.string.chat_you) else name,
                style = TflTheme.typography.labelMd,
                color = if (quote.fromMe) colors.primary else colors.mesh,
                maxLines = 1,
            )
            Text(
                text = quote.text ?: stringResource(R.string.chat_quote_missing),
                style = TflTheme.typography.bodySm,
                color = colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontStyle = if (quote.text == null) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}

@Composable
private fun Reactions(message: MessageView) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        message.reactions.forEach { reaction ->
            Text(
                text = reaction.emoji,
                modifier = Modifier
                    .background(if (reaction.fromMe) colors.primary.copy(alpha = 0.2f) else colors.surfaceContainer, TflTheme.shapes.pill)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = TflTheme.typography.bodyMd,
            )
        }
    }
}
