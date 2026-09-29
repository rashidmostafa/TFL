package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme

/**
 * A key fingerprint in 4-character monospace groups ("7F4B · 889C · …") inside an 8dp key block,
 * so two people can compare it character by character.
 *
 * TalkBack spells each group out, since that is how fingerprints get compared aloud.
 */
@Composable
fun FingerprintBlock(
    groups: List<String>,
    modifier: Modifier = Modifier,
    label: String? = null,
    onCopy: (() -> Unit)? = null,
) {
    val colors = TflTheme.colors
    val shape = TflTheme.shapes.keyBlock
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.canvas, shape)
            .border(1.dp, colors.border, shape)
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) {
            Text(label.uppercase(), style = TflTheme.typography.labelSm, color = colors.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = groups.joinToString(" · "),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = groups.joinToString(", ") { it.toList().joinToString(" ") } },
                style = TflTheme.typography.codeMd.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = colors.primary,
            )
            if (onCopy != null) {
                TflIconButton(
                    symbol = MaterialSymbols.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    onClick = onCopy,
                    containerColor = Color.Transparent,
                    size = 40.dp,
                    iconSize = 18.dp,
                )
            }
        }
    }
}
