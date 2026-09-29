package app.tfl.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

enum class ListRowTone { Default, Danger }

/**
 * A settings-style row: icon in a circle, title (+ optional badge), one-line subtitle, trailing slot.
 *
 * @param standalone draws its own card. Set false for rows grouped inside a [TflCard] with [TflDivider]s.
 * @param trailing defaults to a chevron; pass a [TflSwitch], a [Tag], or null.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    tone: ListRowTone = ListRowTone.Default,
    standalone: Boolean = true,
    titleBadge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { ListRowDefaults.Chevron(tone) },
    onClick: (() -> Unit)? = null,
) {
    val colors = TflTheme.colors
    val danger = tone == ListRowTone.Danger
    val accent = if (danger) colors.danger else colors.primary
    val shape = if (standalone) TflTheme.shapes.card else RectangleShape
    val container = when {
        !standalone -> Color.Transparent
        danger -> colors.danger.copy(alpha = 0.15f).compositeOver(colors.surface)
        else -> colors.surface
    }
    val border = when {
        !standalone -> null
        danger -> BorderStroke(1.dp, colors.danger.copy(alpha = 0.3f))
        else -> BorderStroke(1.dp, colors.border)
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (danger) colors.danger.copy(alpha = 0.2f) else colors.surfaceContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    TflIcon(icon, contentDescription = null, size = 20.dp, tint = accent)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TflTheme.typography.labelLg,
                        color = if (danger) colors.danger else colors.textPrimary,
                    )
                    titleBadge?.invoke()
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        modifier = Modifier.padding(top = 2.dp),
                        style = TflTheme.typography.bodySm,
                        color = if (danger) colors.textPrimary.copy(alpha = 0.8f) else colors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            contentColor = colors.textPrimary,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            contentColor = colors.textPrimary,
            border = border,
            content = content,
        )
    }
}

/**
 * A row whose whole area toggles a setting, announced to TalkBack as a switch.
 * Grouped inside a [TflCard] like a non-standalone [ListRow].
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    enabled: Boolean = true,
) {
    ListRow(
        title = title,
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        subtitle = subtitle,
        icon = icon,
        standalone = false,
        trailing = { TflSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** One option of a single-choice group; the whole row selects it. */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    ListRow(
        title = title,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        subtitle = subtitle,
        standalone = false,
        trailing = { TflRadioButton(selected = selected, onClick = null) },
    )
}

object ListRowDefaults {
    @Composable
    fun Chevron(tone: ListRowTone = ListRowTone.Default) {
        val danger = tone == ListRowTone.Danger
        TflIcon(
            symbol = if (danger) MaterialSymbols.ArrowForward else MaterialSymbols.ChevronRight,
            contentDescription = null,
            size = 20.dp,
            tint = if (danger) TflTheme.colors.danger else TflTheme.colors.textMuted,
            autoMirror = true,
        )
    }
}
