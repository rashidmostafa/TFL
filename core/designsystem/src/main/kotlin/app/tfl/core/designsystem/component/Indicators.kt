package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/** Icon on a rounded-square tile, used as the leading visual of cards and rows. */
@Composable
fun IconTile(
    symbol: String,
    modifier: Modifier = Modifier,
    tint: Color = TflTheme.colors.primary,
    containerColor: Color = TflTheme.colors.surfaceContainer,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(containerColor, TflTheme.shapes.control),
        contentAlignment = Alignment.Center,
    ) {
        TflIcon(symbol, contentDescription = null, size = iconSize, tint = tint)
    }
}

/** Thin rounded progress track. [progress] is 0..1. */
@Composable
fun TflProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = TflTheme.colors.primary,
    trackColor: Color = TflTheme.colors.surfaceContainer,
    height: Dp = 6.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        if (clamped > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .background(color, CircleShape),
            )
        }
    }
}

/** One slice of a [SegmentedBar]: a share of the whole (weights are relative) and its colour. */
data class BarSegment(val weight: Float, val color: Color)

/** Stacked horizontal bar: storage by category, protections enabled, and similar. */
@Composable
fun SegmentedBar(
    segments: List<BarSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    gap: Dp = 3.dp,
    trackColor: Color = TflTheme.colors.surfaceContainer,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        segments.filter { it.weight > 0f }.forEach { segment ->
            Box(
                Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .background(segment.color, CircleShape),
            )
        }
    }
}
