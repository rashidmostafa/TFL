package app.tfl.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.tfl.core.model.Transport

/** Raw palette from design/SCREEN_INDEX.md (not the DESIGN.md frontmatter). UI code reads [TflTheme.colors]. */
internal object TflPalette {
    val Canvas = Color(0xFF0B0F14)
    val Surface = Color(0xFF141A21)
    val SurfaceContainer = Color(0xFF1E2631)
    val Outline = Color(0xFF2A3441)
    val OutlineVariant = Color(0x0AFFFFFF) // white 4%
    val Border = Color(0x14FFFFFF) // white 8%
    val BorderElevated = Color(0x1FFFFFFF) // white 12%

    val Primary = Color(0xFF22D3B6)
    val OnPrimary = Color(0xFF042F2C)
    val Mesh = Color(0xFF38BDF8)
    val OnMesh = Color(0xFF082F49)
    val Tor = Color(0xFFA855F7)
    val OnTor = Color(0xFF3B0764)

    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF5A524)
    val Danger = Color(0xFFEF4444)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextMuted = Color(0xFF94A3B8)
    val TextDim = Color(0xFF64748B)
}

/**
 * TFL's semantic colours. The app is dark-only, so there is a single instance.
 *
 * Luminance layers: [canvas] < [surface] (cards, sheets, incoming bubbles) < [surfaceContainer]
 * (elevated cards, inputs, outgoing bubbles). Depth comes from 1px borders, never drop shadows.
 */
@Immutable
data class TflColors(
    val canvas: Color,
    val surface: Color,
    val surfaceContainer: Color,
    /** Solid 1px borders (ghost buttons, dividers on the canvas). */
    val outline: Color,
    /** Nested dividers inside cards. */
    val outlineVariant: Color,
    /** Card borders. */
    val border: Color,
    /** Borders of elevated containers and sheets. */
    val borderElevated: Color,
    /** E2EE, verified, primary actions, and the Nearby transport. */
    val primary: Color,
    val onPrimary: Color,
    val mesh: Color,
    val onMesh: Color,
    val tor: Color,
    val onTor: Color,
    /** Delivered, verified handshake. */
    val success: Color,
    /** Queued, unverified key, degraded. */
    val warning: Color,
    /** Key mismatch, wipe, SOS. */
    val danger: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textDim: Color,
)

internal val DarkTflColors = TflColors(
    canvas = TflPalette.Canvas,
    surface = TflPalette.Surface,
    surfaceContainer = TflPalette.SurfaceContainer,
    outline = TflPalette.Outline,
    outlineVariant = TflPalette.OutlineVariant,
    border = TflPalette.Border,
    borderElevated = TflPalette.BorderElevated,
    primary = TflPalette.Primary,
    onPrimary = TflPalette.OnPrimary,
    mesh = TflPalette.Mesh,
    onMesh = TflPalette.OnMesh,
    tor = TflPalette.Tor,
    onTor = TflPalette.OnTor,
    success = TflPalette.Success,
    warning = TflPalette.Warning,
    danger = TflPalette.Danger,
    textPrimary = TflPalette.TextPrimary,
    textMuted = TflPalette.TextMuted,
    textDim = TflPalette.TextDim,
)

internal val LocalTflColors = staticCompositionLocalOf { DarkTflColors }

/** Accent colour of a transport: Nearby teal, Mesh blue, Tor purple, Queued amber. */
fun TflColors.forTransport(transport: Transport): Color = when (transport) {
    Transport.NEARBY -> primary
    Transport.MESH -> mesh
    Transport.TOR -> tor
    Transport.QUEUED -> warning
}
