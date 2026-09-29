package app.tfl.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

enum class TflButtonSize(internal val height: Dp) {
    /** Inline and secondary actions. */
    Medium(48.dp),

    /** Full-width calls to action. */
    Large(56.dp),
}

/** Filled teal stadium button for the main action on a screen. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
    size: TflButtonSize = TflButtonSize.Large,
    glow: Boolean = false,
) {
    val colors = TflTheme.colors
    val shape = TflTheme.shapes.pill
    TflButton(
        text = text,
        onClick = onClick,
        modifier = if (glow && enabled) modifier.glow(colors.primary, shape) else modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        containerColor = colors.primary,
        contentColor = colors.onPrimary,
        border = null,
    )
}

/** Transparent button with a 1px border that turns teal while pressed. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
    size: TflButtonSize = TflButtonSize.Large,
) {
    val colors = TflTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    TflButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        containerColor = Color.Transparent,
        contentColor = colors.textPrimary,
        border = BorderStroke(1.dp, if (pressed) colors.primary else colors.outline),
        interactionSource = interactionSource,
    )
}

/** Red-tinted button for irreversible actions: wiping, revoking, locking out. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
    size: TflButtonSize = TflButtonSize.Large,
) {
    val danger = TflTheme.colors.danger
    TflButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
        size = size,
        containerColor = danger.copy(alpha = 0.15f),
        contentColor = danger,
        border = BorderStroke(1.dp, danger),
    )
}

@Composable
private fun TflButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: String?,
    size: TflButtonSize,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    interactionSource: MutableInteractionSource? = null,
) {
    val typography = TflTheme.typography
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height),
        enabled = enabled,
        shape = TflTheme.shapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = containerColor.alpha * 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        border = border,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = interactionSource,
    ) {
        if (icon != null) {
            TflIcon(icon, contentDescription = null, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = when (size) {
                TflButtonSize.Large -> typography.bodyLg.copy(fontWeight = FontWeight.SemiBold)
                TflButtonSize.Medium -> typography.labelLg
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small tonal pill for inline actions such as "Scan", "Split" or "Resolve key". */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    trailingIcon: String? = null,
    contentColor: Color = TflTheme.colors.primary,
    containerColor: Color = TflTheme.colors.surfaceContainer,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button },
        enabled = enabled,
        shape = TflTheme.shapes.pill,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) TflIcon(icon, contentDescription = null, size = 18.dp)
            Text(
                text = text,
                style = TflTheme.typography.codeMd.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            if (trailingIcon != null) TflIcon(trailingIcon, contentDescription = null, size = 16.dp, autoMirror = true)
        }
    }
}

/** Circular icon-only button. [contentDescription] is required: an icon alone says nothing to TalkBack. */
@Composable
fun TflIconButton(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = TflTheme.colors.surfaceContainer,
    tint: Color = TflTheme.colors.textMuted,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
    filled: Boolean = false,
    autoMirror: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .semantics { role = Role.Button },
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TflIcon(symbol, contentDescription, size = iconSize, filled = filled, autoMirror = autoMirror)
        }
    }
}
