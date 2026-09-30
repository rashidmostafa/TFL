package app.tfl.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.theme.TflTheme
import kotlin.math.floor
import kotlin.math.roundToInt

/** A QR code's modules, row by row (true = dark). The feature that owns the text encodes it. */
@Immutable
class QrMatrix(val size: Int, private val dark: BooleanArray) {
    init {
        require(size > 0 && dark.size == size * size) { "A QR matrix has size × size modules" }
    }

    fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
}

/**
 * A QR code, dark modules on a light square with the standard 4-module quiet zone: the contrast
 * every scanner reads best. Modules snap to whole pixels, so none blur into their neighbours.
 */
@Composable
fun QrCode(matrix: QrMatrix, contentDescription: String, modifier: Modifier = Modifier) {
    val light = TflTheme.colors.textPrimary
    val dark = TflTheme.colors.canvas
    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val modules = matrix.size + 2 * QUIET_ZONE
        val module = floor(size.minDimension / modules).coerceAtLeast(1f)
        val side = module * modules
        val left = ((size.width - side) / 2).roundToInt().toFloat()
        val top = ((size.height - side) / 2).roundToInt().toFloat()
        drawRoundRect(light, Offset(left, top), Size(side, side), CornerRadius(module * 2))
        for (y in 0 until matrix.size) {
            var x = 0
            while (x < matrix.size) {
                if (!matrix.isDark(x, y)) {
                    x++
                    continue
                }
                val start = x
                while (x < matrix.size && matrix.isDark(x, y)) x++
                drawRect(
                    dark,
                    Offset(left + (start + QUIET_ZONE) * module, top + (y + QUIET_ZONE) * module),
                    Size((x - start) * module, module),
                )
            }
        }
    }
}

/** L-shaped marks in the four corners, framing a QR code or a camera viewfinder. */
fun Modifier.cornerMarks(color: Color, length: Dp = 24.dp, width: Dp = 3.dp): Modifier = drawWithContent {
    drawContent()
    val stroke = width.toPx()
    val arm = length.toPx()
    val inset = stroke / 2
    val corners = listOf(
        floatArrayOf(inset, inset, 1f, 1f),
        floatArrayOf(size.width - inset, inset, -1f, 1f),
        floatArrayOf(inset, size.height - inset, 1f, -1f),
        floatArrayOf(size.width - inset, size.height - inset, -1f, -1f),
    )
    for ((x, y, dx, dy) in corners) {
        val mark = Path().apply {
            moveTo(x, y + dy * arm)
            lineTo(x, y)
            lineTo(x + dx * arm, y)
        }
        drawPath(mark, color, style = Stroke(stroke, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
}

private const val QUIET_ZONE = 4
