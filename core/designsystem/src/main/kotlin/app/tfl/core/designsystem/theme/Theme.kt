package app.tfl.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Material 3 roles mapped from the TFL palette, so stock M3 components match without per-call colours. */
private val TflColorScheme = darkColorScheme(
    primary = TflPalette.Primary,
    onPrimary = TflPalette.OnPrimary,
    primaryContainer = TflPalette.Primary.copy(alpha = 0.12f).compositeOver(TflPalette.Surface),
    onPrimaryContainer = TflPalette.Primary,
    inversePrimary = Color(0xFF0F766E),
    secondary = TflPalette.Mesh,
    onSecondary = TflPalette.OnMesh,
    secondaryContainer = TflPalette.Mesh.copy(alpha = 0.12f).compositeOver(TflPalette.Surface),
    onSecondaryContainer = TflPalette.Mesh,
    tertiary = TflPalette.Tor,
    onTertiary = TflPalette.OnTor,
    tertiaryContainer = TflPalette.Tor.copy(alpha = 0.12f).compositeOver(TflPalette.Surface),
    onTertiaryContainer = TflPalette.Tor,
    background = TflPalette.Canvas,
    onBackground = TflPalette.TextPrimary,
    surface = TflPalette.Canvas,
    onSurface = TflPalette.TextPrimary,
    surfaceVariant = TflPalette.SurfaceContainer,
    onSurfaceVariant = TflPalette.TextMuted,
    // No tonal-elevation tint: depth comes from luminance layers and borders.
    surfaceTint = Color.Transparent,
    inverseSurface = TflPalette.TextPrimary,
    inverseOnSurface = TflPalette.Canvas,
    error = TflPalette.Danger,
    onError = TflPalette.TextPrimary,
    errorContainer = TflPalette.Danger.copy(alpha = 0.15f).compositeOver(TflPalette.Surface),
    onErrorContainer = TflPalette.Danger,
    outline = TflPalette.Outline,
    outlineVariant = TflPalette.OutlineVariant,
    scrim = Color.Black,
    surfaceBright = TflPalette.SurfaceContainer,
    surfaceDim = TflPalette.Canvas,
    surfaceContainerLowest = TflPalette.Canvas,
    surfaceContainerLow = TflPalette.Surface,
    surfaceContainer = TflPalette.Surface,
    surfaceContainerHigh = TflPalette.SurfaceContainer,
    surfaceContainerHighest = TflPalette.SurfaceContainer,
)

/** TFL's theme. Dark-only by design; there is no light palette and no dynamic colour. */
@Composable
fun TflTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTflColors provides DarkTflColors,
        LocalTflTypography provides DefaultTflTypography,
        LocalTflShapes provides TflShapes(),
    ) {
        MaterialTheme(
            colorScheme = TflColorScheme,
            typography = DefaultTflTypography.toMaterial(),
            shapes = TflMaterialShapes,
            content = content,
        )
    }
}

object TflTheme {
    val colors: TflColors
        @Composable @ReadOnlyComposable get() = LocalTflColors.current

    val typography: TflTypography
        @Composable @ReadOnlyComposable get() = LocalTflTypography.current

    val shapes: TflShapes
        @Composable @ReadOnlyComposable get() = LocalTflShapes.current

    val spacing: TflSpacing get() = TflSpacing
}
