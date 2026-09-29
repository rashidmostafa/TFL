package app.tfl.core.designsystem.icon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R

private val SymbolsOutlined = FontFamily(Font(R.font.material_symbols_outlined))
private val SymbolsFilled = FontFamily(Font(R.font.material_symbols_outlined_filled))

/**
 * A Material Symbols Outlined glyph from the bundled font.
 *
 * @param symbol a [MaterialSymbols] constant.
 * @param contentDescription what the icon means, for TalkBack. Pass null for decorative icons,
 *   which are then hidden from accessibility services.
 * @param size stays fixed in dp whatever the user's font scale.
 * @param filled the FILL=1 variant, used for selected and emphasised states.
 * @param autoMirror flips directional glyphs (arrows, chevrons) in right-to-left layouts.
 */
@Composable
fun TflIcon(
    symbol: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = false,
    autoMirror: Boolean = false,
) {
    val mirrored = autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val style = remember(size, filled, density) {
        // Glyphs fill exactly 1em; the font's 0.1em of ascent/descent padding is centred away.
        val fontSize = with(density) { size.toSp() }
        TextStyle(
            fontFamily = if (filled) SymbolsFilled else SymbolsOutlined,
            fontSize = fontSize,
            lineHeight = fontSize,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
    Text(
        text = symbol,
        modifier = modifier
            .size(size)
            .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            },
        color = tint,
        maxLines = 1,
        softWrap = false,
        style = style,
    )
}
