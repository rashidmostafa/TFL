package app.tfl.feature.chats.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.ChoiceCard
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.ListRowTone
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SheetBody
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.label
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.message.MessageLimits
import app.tfl.feature.chats.R
import app.tfl.feature.chats.TimeContext
import app.tfl.feature.chats.clockTimeWithSeconds
import app.tfl.feature.chats.dayLabel
import app.tfl.feature.chats.timerLabel

/** The reactions offered on every message. */
internal val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** A message's long-press menu: reactions, then what can be done with it. */
@Composable
internal fun MessageActionsSheet(
    actions: MessageActions,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit,
) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        MessageActionsContent(actions, onReact, onReply, onEdit, onCopy, onInfo, onDeleteForMe, onDeleteForEveryone)
    }
}

@Composable
internal fun ColumnScope.MessageActionsContent(
    actions: MessageActions,
    onReact: (String) -> Unit = {},
    onReply: () -> Unit = {},
    onEdit: () -> Unit = {},
    onCopy: () -> Unit = {},
    onInfo: () -> Unit = {},
    onDeleteForMe: () -> Unit = {},
    onDeleteForEveryone: () -> Unit = {},
) {
    SheetBody(spacing = 8.dp) {
        if (!actions.message.deleted) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QUICK_REACTIONS.forEach { emoji -> ReactionButton(emoji, chosen = emoji == actions.myReaction, onClick = { onReact(emoji) }) }
            }
        }
        if (actions.canReply) ListRow(stringResource(R.string.chat_reply), icon = MaterialSymbols.Reply, trailing = null, onClick = onReply)
        if (actions.canEdit) {
            ListRow(
                title = stringResource(R.string.chat_edit),
                subtitle = pluralStringResource(R.plurals.chat_edit_minutes_left, actions.editMinutesLeft, actions.editMinutesLeft),
                icon = MaterialSymbols.Edit,
                trailing = null,
                onClick = onEdit,
            )
        }
        if (!actions.message.deleted) ListRow(stringResource(R.string.chat_copy), icon = MaterialSymbols.ContentCopy, trailing = null, onClick = onCopy)
        ListRow(stringResource(R.string.chat_info), icon = MaterialSymbols.Info, trailing = null, onClick = onInfo)
        ListRow(stringResource(R.string.chat_delete_for_me), icon = MaterialSymbols.Delete, trailing = null, onClick = onDeleteForMe)
        if (actions.canDeleteForEveryone) {
            ListRow(
                title = stringResource(R.string.chat_delete_for_everyone),
                icon = MaterialSymbols.DeleteForever,
                tone = ListRowTone.Danger,
                trailing = null,
                onClick = onDeleteForEveryone,
            )
        }
    }
}

@Composable
private fun ReactionButton(emoji: String, chosen: Boolean, onClick: () -> Unit) {
    val colors = TflTheme.colors
    val label = stringResource(if (chosen) R.string.chat_reaction_remove else R.string.chat_reaction_add, emoji)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                selected = chosen
            },
        shape = CircleShape,
        color = if (chosen) colors.primary.copy(alpha = 0.25f) else colors.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(emoji, style = TflTheme.typography.headlineSm) }
    }
}

/** What's known about a message: times, how it travelled, and what protects it, as the code does it. */
@Composable
internal fun MessageInfoSheet(info: MessageInfo, name: String, time: TimeContext, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        MessageInfoContent(info, name, time, onDismiss)
    }
}

@Composable
internal fun ColumnScope.MessageInfoContent(info: MessageInfo, name: String, time: TimeContext, onClose: () -> Unit = {}) {
    val colors = TflTheme.colors
    SheetHeader(stringResource(R.string.chat_info_title), icon = MaterialSymbols.Info, onClose = onClose)
    SheetBody {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.canvas, TflTheme.shapes.control)
                .border(1.dp, colors.border, TflTheme.shapes.control)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            @Composable
            fun at(millis: Long) = "${time.dayLabel(millis)}, ${time.clockTimeWithSeconds(millis)}"
            if (info.outgoing) {
                InfoRow(stringResource(R.string.chat_info_written), at(info.writtenAtMillis))
                info.scheduledForMillis?.let { InfoRow(stringResource(R.string.chat_info_scheduled), at(it)) }
                InfoRow(stringResource(R.string.chat_info_sent), info.sentAtMillis?.let { at(it) } ?: stringResource(R.string.chat_info_not_yet))
                InfoRow(
                    stringResource(R.string.chat_info_delivered),
                    when {
                        info.deliveredAtMillis != null -> at(info.deliveredAtMillis)
                        info.status == DeliveryStatus.FAILED -> stringResource(R.string.chat_info_failed)
                        else -> stringResource(R.string.chat_info_not_yet)
                    },
                )
            } else {
                InfoRow(stringResource(R.string.chat_info_written_their_clock, name), at(info.writtenAtMillis))
                info.receivedAtMillis?.let { InfoRow(stringResource(R.string.chat_info_received), at(it)) }
            }
            info.editedAtMillis?.let { InfoRow(stringResource(R.string.chat_info_edited), at(it)) }
            info.disappearsAtMillis?.let { InfoRow(stringResource(R.string.chat_info_disappears), at(it)) }
            InfoRow(stringResource(R.string.chat_info_transport), info.transport?.label() ?: stringResource(R.string.chat_info_not_yet))
            InfoRow(stringResource(R.string.chat_info_id), info.shortId, mono = true)
        }
        Callout(
            title = stringResource(R.string.chat_info_protection_title),
            text = stringResource(if (info.outgoing) R.string.chat_info_protection_outgoing else R.string.chat_info_protection_incoming, name),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    val colors = TflTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = TflTheme.typography.bodySm, color = colors.textMuted)
        Text(value, style = if (mono) TflTheme.typography.codeSm else TflTheme.typography.bodySm, color = colors.textPrimary)
    }
}

/** Off, 5 minutes, 1 hour, 1 day or 1 week, for new messages on both phones. */
@Composable
internal fun TimerSheet(current: Int, name: String, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        TimerContent(current, name, onSet, onDismiss)
    }
}

@Composable
internal fun ColumnScope.TimerContent(current: Int, name: String, onSet: (Int) -> Unit = {}, onDismiss: () -> Unit = {}, initialChoice: Int = current) {
    var choice by rememberSaveable { mutableIntStateOf(initialChoice) }
    SheetHeader(stringResource(R.string.chat_disappearing), icon = MaterialSymbols.AutoDelete, onClose = onDismiss)
    SheetBody(spacing = 8.dp) {
        Callout(text = stringResource(R.string.chat_disappearing_explained, name))
        MessageLimits.TIMER_SECONDS.forEach { seconds ->
            ChoiceCard(
                title = timerLabel(seconds),
                body = stringResource(
                    when (seconds) {
                        0 -> R.string.chat_timer_off_body
                        300 -> R.string.chat_timer_minutes_body
                        3_600 -> R.string.chat_timer_hour_body
                        86_400 -> R.string.chat_timer_day_body
                        else -> R.string.chat_timer_week_body
                    },
                ),
                selected = choice == seconds,
                onSelect = { choice = seconds },
                icon = when (seconds) {
                    0 -> MaterialSymbols.TimerOff
                    300 -> MaterialSymbols.Bolt
                    3_600 -> MaterialSymbols.HourglassTop
                    86_400 -> MaterialSymbols.CalendarToday
                    else -> MaterialSymbols.DateRange
                },
            )
        }
        PrimaryButton(
            text = if (choice == 0) stringResource(R.string.chat_timer_turn_off) else stringResource(R.string.chat_timer_set, timerLabel(choice)),
            onClick = { onSet(choice) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            enabled = choice != current,
        )
        GhostButton(stringResource(R.string.chat_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun DeleteForEveryoneSheet(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.chat_delete_for_everyone), icon = MaterialSymbols.DeleteForever, onClose = onDismiss)
        SheetBody {
            Callout(text = stringResource(R.string.chat_delete_for_everyone_explained, name))
            DestructiveButton(stringResource(R.string.chat_delete_for_everyone), onClick = onDelete, modifier = Modifier.fillMaxWidth())
            GhostButton(stringResource(R.string.chat_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
