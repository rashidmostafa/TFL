package app.tfl.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.theme.TflSpacing
import app.tfl.core.designsystem.theme.TflTheme

/**
 * Surface-layer container: 16dp corners and a 1px border instead of a shadow.
 * Clickable when [onClick] is set.
 */
@Composable
fun TflCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = TflTheme.colors.surface,
    borderColor: Color = TflTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(TflSpacing.lg),
    verticalSpacing: Dp = TflSpacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = TflTheme.shapes.card
    val border = BorderStroke(1.dp, borderColor)
    val contentColor = TflTheme.colors.textPrimary
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = body,
        )
    }
}

/** 1px divider for rows nested inside a card. */
@Composable
fun TflDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = TflTheme.colors.outlineVariant)
}
