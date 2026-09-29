package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/** Centred icon, title, explanation and optional action for a screen or list with nothing to show. */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .glow(colors.primary, CircleShape, alpha = 0.2f)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TflIcon(icon, contentDescription = null, size = 32.dp, tint = colors.primary)
        }
        Text(title, style = TflTheme.typography.headlineSm, color = colors.textPrimary, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}
