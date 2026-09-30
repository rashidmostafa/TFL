package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/**
 * TFL's number pad. PINs are entered here rather than through the system keyboard, so no keyboard
 * app ever sees them and they never become a String.
 *
 * @param bottomStart optional key left of 0, typically the fingerprint button.
 */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bottomStart: (@Composable RowScope.() -> Unit)? = null,
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bottomStart != null) bottomStart() else Spacer(Modifier.weight(1f))
            DigitKey(0, enabled, onDigit)
            val delete = stringResource(R.string.pin_delete)
            PadKey(onClick = onDelete, enabled = enabled, contentDescription = delete) {
                TflIcon(MaterialSymbols.Backspace, contentDescription = null, size = 26.dp, tint = TflTheme.colors.textPrimary, autoMirror = true)
            }
        }
    }
}

/** A pad-shaped key for [PinPad]'s bottom-start slot. */
@Composable
fun RowScope.PinPadIconKey(symbol: String, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    PadKey(onClick = onClick, enabled = enabled, contentDescription = contentDescription) {
        TflIcon(symbol, contentDescription = null, size = 28.dp, tint = TflTheme.colors.mesh)
    }
}

@Composable
private fun RowScope.DigitKey(digit: Int, enabled: Boolean, onDigit: (Int) -> Unit) {
    PadKey(onClick = { onDigit(digit) }, enabled = enabled, contentDescription = digit.toString()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                style = TflTheme.typography.headlineMd.copy(fontSize = 26.sp, lineHeight = 30.sp),
                color = TflTheme.colors.textPrimary,
            )
            LETTERS[digit]?.let {
                Text(it, style = TflTheme.typography.labelSm.copy(fontSize = 10.sp), color = TflTheme.colors.textMuted)
            }
        }
    }
}

@Composable
private fun RowScope.PadKey(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        shape = TflTheme.shapes.card,
        color = TflTheme.colors.surface,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * One dot per PIN digit. Announced as "3 of 6 digits entered" rather than read digit by digit.
 */
@Composable
fun PinDots(
    entered: Int,
    modifier: Modifier = Modifier,
    length: Int = 6,
    error: Boolean = false,
) {
    val colors = TflTheme.colors
    val description = stringResource(R.string.pin_progress, entered, length)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) { index ->
            val filled = index < entered
            val color = if (error) colors.danger else colors.primary
            Box(
                Modifier
                    .size(18.dp)
                    .then(if (filled) Modifier.glow(color, CircleShape, radius = 8.dp) else Modifier)
                    .background(if (filled) color else colors.surfaceContainer, CircleShape)
                    .border(1.dp, if (filled) color else colors.outline, CircleShape),
            )
        }
    }
}

private val LETTERS = mapOf(
    2 to "ABC", 3 to "DEF", 4 to "GHI", 5 to "JKL", 6 to "MNO", 7 to "PQRS", 8 to "TUV", 9 to "WXYZ", 0 to "+",
)

/** Section label such as "DEVICE SECURITY" with "Step 2 of 6" and a progress bar. */
@Composable
fun StepHeader(label: String, step: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
            Text(
                text = label.uppercase(),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = TflTheme.typography.labelSm.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.step_of, step, total),
                modifier = Modifier
                    .background(colors.surfaceContainer, TflTheme.shapes.pill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                style = TflTheme.typography.labelSm,
                color = colors.textPrimary,
            )
        }
        TflProgressBar(progress = step.toFloat() / total, height = 4.dp)
    }
}
