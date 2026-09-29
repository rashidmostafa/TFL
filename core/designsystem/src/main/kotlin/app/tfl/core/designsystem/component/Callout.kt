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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

enum class CalloutTone { Info, Success, Warning, Danger }

/** Tinted explanation box: an icon, an optional title and a short paragraph. */
@Composable
fun Callout(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: CalloutTone = CalloutTone.Info,
) {
    val colors = TflTheme.colors
    val (color, symbol) = when (tone) {
        CalloutTone.Info -> colors.primary to MaterialSymbols.Info
        CalloutTone.Success -> colors.success to MaterialSymbols.VerifiedUser
        CalloutTone.Warning -> colors.warning to MaterialSymbols.Warning
        CalloutTone.Danger -> colors.danger to MaterialSymbols.Error
    }
    val shape = TflTheme.shapes.control
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), shape)
            .border(1.dp, color.copy(alpha = 0.3f), shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TflIcon(symbol, contentDescription = null, size = 20.dp, tint = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) Text(title, style = TflTheme.typography.labelLg, color = color)
            Text(text, style = TflTheme.typography.bodySm, color = colors.textPrimary.copy(alpha = 0.85f))
        }
    }
}
