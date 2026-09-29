package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/** Bottom navigation: 64dp on the canvas with a 1px top border. Pads itself above the system navigation bar. */
@Composable
fun TflNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = TflTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .drawBehind {
                drawLine(colors.border, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A tab: the selected one gets a surfaceContainer pill behind a filled teal icon, and a teal label. */
@Composable
fun RowScope.TflNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    val tint = if (selected) colors.primary else colors.textMuted
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 32.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 28.dp)
                .background(if (selected) colors.surfaceContainer else Color.Transparent, TflTheme.shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            TflIcon(symbol, contentDescription = null, size = 22.dp, tint = tint, filled = selected)
        }
        Spacer(Modifier.height(2.dp))
        Text(label, style = TflTheme.typography.labelSm, color = tint, maxLines = 1)
    }
}
