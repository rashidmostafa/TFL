package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.theme.TflTheme

/**
 * A safety number: groups of digits (12 × 5) in rows of four, the layout both phones show so two
 * people can read them to each other. Rows wrap to fewer groups when large text needs the room.
 * TalkBack reads the digits one at a time, group by group.
 */
@Composable
fun SafetyNumberGrid(groups: List<String>, modifier: Modifier = Modifier, color: Color = TflTheme.colors.primary) {
    val colors = TflTheme.colors
    val shape = TflTheme.shapes.keyBlock
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas, shape)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clearAndSetSemantics {
                contentDescription = groups.joinToString(", ") { it.toList().joinToString(" ") }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = COLUMNS,
    ) {
        groups.forEach { group ->
            Text(
                text = group,
                style = TflTheme.typography.codeMd.copy(fontSize = 17.sp, lineHeight = 24.sp),
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private const val COLUMNS = 4
