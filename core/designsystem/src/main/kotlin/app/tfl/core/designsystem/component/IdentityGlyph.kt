package app.tfl.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.theme.TflTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Key art drawn from a fingerprint: a hexagonal mesh whose node colours, sizes and links come from
 * the fingerprint's bytes, so the same identity always draws the same picture. Decorative: the
 * fingerprint text is what people compare.
 */
@Composable
fun IdentityGlyph(fingerprintHex: String, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val colors = TflTheme.colors
    val bytes = remember(fingerprintHex) {
        fingerprintHex.chunked(2).mapNotNull { it.toIntOrNull(16) }.ifEmpty { List(16) { 0 } }
    }
    val palette = listOf(colors.primary, colors.mesh, colors.tor)
    Canvas(
        modifier
            .size(size)
            .clearAndSetSemantics {},
    ) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val radius = this.size.minDimension / 2
        drawCircle(colors.canvas, radius = radius)
        drawCircle(colors.outline, radius = radius, style = Stroke(1.dp.toPx()))

        val ring = radius * 0.62f
        val nodes = (0 until 6).map { i ->
            val angle = -PI / 2 + i * PI / 3
            Offset(center.x + (ring * cos(angle)).toFloat(), center.y + (ring * sin(angle)).toFloat())
        }
        val byte = { i: Int -> bytes[i % bytes.size] }

        // Hexagon outline, dashed.
        nodes.indices.forEach { i ->
            drawLine(
                colors.textDim.copy(alpha = 0.5f), nodes[i], nodes[(i + 1) % 6], 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }
        // Spokes: at least two, chosen by the fingerprint.
        val spokes = byte(6) or (1 shl (byte(7) % 6)) or (1 shl ((byte(7) / 6 + 3) % 6))
        nodes.forEachIndexed { i, node ->
            if (spokes shr i and 1 == 1) drawLine(colors.primary.copy(alpha = 0.8f), center, node, 2.dp.toPx())
        }
        // Chords across the hexagon.
        nodes.indices.forEach { i ->
            if (byte(8) shr i and 1 == 1) drawLine(colors.mesh.copy(alpha = 0.35f), nodes[i], nodes[(i + 2) % 6], 1.dp.toPx())
        }
        nodes.forEachIndexed { i, node ->
            val color: Color = palette[byte(i) % palette.size]
            val nodeRadius = (4 + byte(i + 9) % 4).dp.toPx()
            drawCircle(color.copy(alpha = 0.25f), radius = nodeRadius * 1.8f, center = node)
            drawCircle(color, radius = nodeRadius, center = node)
        }
        drawCircle(colors.primary.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = center)
        drawCircle(colors.canvas, radius = 11.dp.toPx(), center = center)
        drawCircle(colors.primary, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(colors.primary, radius = 5.dp.toPx(), center = center)
    }
}
