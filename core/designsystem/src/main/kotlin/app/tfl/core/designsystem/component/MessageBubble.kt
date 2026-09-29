package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport

enum class BubbleDirection { Incoming, Outgoing }

/**
 * Chat bubble container. Incoming: surface with a white-8% border, tight top-start corner.
 * Outgoing: surfaceContainer with a teal-25% border, tight top-end corner. The caller aligns it.
 *
 * @param footer transport chip and [BubbleMeta], laid out under the content.
 */
@Composable
fun MessageBubble(
    direction: BubbleDirection,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TflTheme.colors
    val shapes = TflTheme.shapes
    val incoming = direction == BubbleDirection.Incoming
    val shape = if (incoming) shapes.bubbleIncoming else shapes.bubbleOutgoing
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .background(if (incoming) colors.surface else colors.surfaceContainer, shape)
            .border(1.dp, if (incoming) colors.border else colors.primary.copy(alpha = 0.25f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            content()
        }
        if (footer != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = footer,
            )
        }
    }
}

/** Monospace time and, for outgoing messages, delivery status. */
@Composable
fun BubbleMeta(
    time: String,
    modifier: Modifier = Modifier,
    status: DeliveryStatus? = null,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, style = TflTheme.typography.codeSm, color = TflTheme.colors.textMuted)
        if (status != null) DeliveryStatusIcon(status)
    }
}

/** Queued (amber clock), sent (✓), delivered (✓✓) or read (teal ✓✓), announced by name. */
@Composable
fun DeliveryStatusIcon(status: DeliveryStatus, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val (symbol, tint, label) = when (status) {
        DeliveryStatus.QUEUED -> Triple(MaterialSymbols.Schedule, colors.warning, R.string.delivery_queued)
        DeliveryStatus.SENT -> Triple(MaterialSymbols.Check, colors.textMuted, R.string.delivery_sent)
        DeliveryStatus.DELIVERED -> Triple(MaterialSymbols.DoneAll, colors.textMuted, R.string.delivery_delivered)
        DeliveryStatus.READ -> Triple(MaterialSymbols.DoneAll, colors.primary, R.string.delivery_read)
    }
    TflIcon(symbol, contentDescription = stringResource(label), modifier = modifier, size = 16.dp, tint = tint)
}

/** A text message with its transport chip and metadata footer. */
@Composable
fun TextBubble(
    text: String,
    direction: BubbleDirection,
    time: String,
    modifier: Modifier = Modifier,
    transport: Transport? = null,
    status: DeliveryStatus? = null,
) {
    MessageBubble(
        direction = direction,
        modifier = modifier,
        footer = {
            if (transport != null) TransportChip(transport)
            Spacer(Modifier.weight(1f))
            BubbleMeta(time, status = if (direction == BubbleDirection.Outgoing) status else null)
        },
    ) {
        Text(text, style = TflTheme.typography.bodyLg)
    }
}
