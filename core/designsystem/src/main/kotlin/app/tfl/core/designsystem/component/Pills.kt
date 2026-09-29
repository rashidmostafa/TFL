package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/** Stadium-shaped row shared by chips, badges and status pills. */
@Composable
internal fun Pill(
    containerColor: Color,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
    minHeight: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = TflTheme.shapes.pill
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .background(containerColor, shape)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Live status such as "● MESH · 12 PEERS": a dot (or an icon) and monospace text. */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TflTheme.colors.primary,
    icon: String? = null,
) {
    Pill(
        containerColor = TflTheme.colors.surfaceContainer,
        modifier = modifier.semantics(mergeDescendants = true) {},
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        minHeight = 28.dp,
    ) {
        if (icon != null) {
            TflIcon(icon, contentDescription = null, size = 14.dp, tint = color)
        } else {
            Box(Modifier.size(6.dp).background(color, CircleShape))
        }
        Text(text, style = TflTheme.typography.labelSm, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Short emphasis label such as "VERIFIED", "Hardened" or "ALERT".
 *
 * @param solid fills the tag with [color] instead of a 15% tint.
 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TflTheme.colors.primary,
    solid: Boolean = false,
    contentColor: Color = if (solid) TflTheme.colors.textPrimary else color,
) {
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = text,
        modifier = modifier
            .background(if (solid) color else color.copy(alpha = 0.15f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = TflTheme.typography.labelSm,
        color = contentColor,
        maxLines = 1,
    )
}

/** Unread or pending count in a small circle. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = TflTheme.colors.primary,
    contentColor: Color = TflTheme.colors.onPrimary,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .background(color, CircleShape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = TflTheme.typography.labelSm.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = contentColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}
