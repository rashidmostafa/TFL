package app.tfl.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme

/**
 * 64dp screen header on the canvas with a 1px bottom border. Pads itself below the status bar.
 *
 * @param subtitle usually a [StatusLine] under the title.
 * @param navigationIcon a [TflBackButton] or [TflLogo].
 */
@Composable
fun TflTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TflTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(colors.border, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigationIcon?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = TflTheme.typography.headlineSm,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.invoke()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Dot plus monospace status text, e.g. "● 4 peers · Tor on". */
@Composable
fun StatusLine(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TflTheme.colors.primary,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, TflTheme.shapes.pill))
        Text(
            text = text,
            style = TflTheme.typography.codeSm,
            color = TflTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The TFL shield logo (design/assets/tfl_logo.svg). Decorative. */
@Composable
fun TflLogo(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Image(
        painter = painterResource(R.drawable.tfl_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun TflBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TflIconButton(
        symbol = MaterialSymbols.ArrowBack,
        contentDescription = stringResource(R.string.action_back),
        onClick = onClick,
        modifier = modifier,
        containerColor = Color.Transparent,
        tint = TflTheme.colors.textPrimary,
        size = 40.dp,
        autoMirror = true,
    )
}

/** Teal circle with a person glyph, top-right on every tab: opens your identity. */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TflIconButton(
        symbol = MaterialSymbols.Person,
        contentDescription = stringResource(R.string.action_profile),
        onClick = onClick,
        modifier = modifier,
        containerColor = TflTheme.colors.primary,
        tint = TflTheme.colors.onPrimary,
        size = 36.dp,
        iconSize = 20.dp,
    )
}
