package app.tfl.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft bloom in a transport colour, which TFL uses instead of cast shadows for critical
 * elements (primary actions, active nodes, panic triggers).
 */
fun Modifier.glow(color: Color, shape: Shape, radius: Dp = 14.dp, alpha: Float = 0.3f): Modifier =
    dropShadow(shape, Shadow(radius = radius, color = color.copy(alpha = alpha)))
