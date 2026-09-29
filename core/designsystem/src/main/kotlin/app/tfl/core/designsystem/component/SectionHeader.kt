package app.tfl.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/** Uppercase monospace label above a group, e.g. "ACTIVE THREADS" … "E2EE VERIFIED". */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    iconTint: Color = TflTheme.colors.primary,
    trailing: String? = null,
    trailingColor: Color = TflTheme.colors.textMuted,
    onTrailingClick: (() -> Unit)? = null,
) {
    val typography = TflTheme.typography
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) TflIcon(icon, contentDescription = null, size = 14.dp, tint = iconTint)
        Text(
            text = title.uppercase(),
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = typography.codeSm.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em),
            color = TflTheme.colors.textMuted,
            maxLines = 1,
        )
        if (trailing != null) {
            Text(
                text = trailing,
                modifier = if (onTrailingClick != null) {
                    Modifier
                        .clickable(role = Role.Button, onClick = onTrailingClick)
                        .padding(vertical = 4.dp)
                } else {
                    Modifier
                },
                style = typography.codeSm,
                color = trailingColor,
                maxLines = 1,
            )
        }
    }
}
