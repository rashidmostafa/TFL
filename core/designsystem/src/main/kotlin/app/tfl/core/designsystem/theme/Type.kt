package app.tfl.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.R

// Bundled variable fonts (see tools/fonts/build_fonts.py); nothing is downloaded at runtime.
private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Headlines. */
val PlusJakartaSans = FontFamily(
    variable(R.font.plus_jakarta_sans, FontWeight.Medium),
    variable(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variable(R.font.plus_jakarta_sans, FontWeight.Bold),
)

/** Body text and UI labels. */
val Inter = FontFamily(
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
    variable(R.font.inter, FontWeight.SemiBold),
    variable(R.font.inter, FontWeight.Bold),
)

/** Keys, fingerprints, hop counts and telemetry: never ambiguous between 0/O or 1/l. */
val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, FontWeight.Normal),
    variable(R.font.jetbrains_mono, FontWeight.Medium),
    variable(R.font.jetbrains_mono, FontWeight.SemiBold),
    variable(R.font.jetbrains_mono, FontWeight.Bold),
)

/** The type roles from the DESIGN.md frontmatter `typography` block. */
@Immutable
data class TflTypography(
    val headlineLg: TextStyle,
    val headlineLgMobile: TextStyle,
    val headlineMd: TextStyle,
    val headlineSm: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    /** Monospace: transport chips, section labels, navigation labels. */
    val labelSm: TextStyle,
    /** Monospace: keys, fingerprints, timestamps. */
    val codeMd: TextStyle,
    val codeSm: TextStyle,
)

private val Base = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

internal val DefaultTflTypography = TflTypography(
    headlineLg = Base.copy(
        fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em,
    ),
    headlineLgMobile = Base.copy(
        fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em,
    ),
    headlineMd = Base.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSm = Base.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLg = Base.copy(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.01.em),
    bodyMd = Base.copy(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySm = Base.copy(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLg = Base.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em),
    labelMd = Base.copy(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSm = Base.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em,
    ),
    codeMd = Base.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em,
    ),
    codeSm = Base.copy(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.05.em,
    ),
)

internal val LocalTflTypography = staticCompositionLocalOf { DefaultTflTypography }

/** Maps the TFL roles onto Material 3 so stock components pick up the same fonts. */
internal fun TflTypography.toMaterial() = Typography(
    displayLarge = headlineLg,
    displayMedium = headlineLg,
    displaySmall = headlineLgMobile,
    headlineLarge = headlineLg,
    headlineMedium = headlineLgMobile,
    headlineSmall = headlineMd,
    titleLarge = headlineMd,
    titleMedium = headlineSm,
    titleSmall = labelLg,
    bodyLarge = bodyLg,
    bodyMedium = bodyMd,
    bodySmall = bodySm,
    labelLarge = labelLg,
    labelMedium = labelMd,
    labelSmall = labelSm,
)
