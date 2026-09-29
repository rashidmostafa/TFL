package app.tfl.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class TflShapes(
    val card: Shape = RoundedCornerShape(16.dp),
    /** Bottom sheets and floating panels: rounded on top only. */
    val sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    /** Chips, pills, status indicators and filter tabs. */
    val pill: Shape = CircleShape,
    /** Frames monospace keys and fingerprints inside cards. */
    val keyBlock: Shape = RoundedCornerShape(8.dp),
    /** Icon tiles, inputs and callouts. */
    val control: Shape = RoundedCornerShape(12.dp),
    /** Incoming bubbles sit on the start side, so their top-start corner is the tight one. */
    val bubbleIncoming: Shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
    /** Outgoing bubbles sit on the end side: tight top-end corner. */
    val bubbleOutgoing: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
)

internal val LocalTflShapes = staticCompositionLocalOf { TflShapes() }

internal val TflMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
