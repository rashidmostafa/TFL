package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.designsystem.theme.forTransport
import app.tfl.core.model.Transport

/** Small marker on an avatar's bottom-end edge. */
sealed interface AvatarBadge {
    /** Dot in the colour of the transport this contact is reachable over right now. */
    data class Reachable(val transport: Transport) : AvatarBadge

    /** Red "!" for something that needs attention, such as a changed key. */
    data object Alert : AvatarBadge
}

/**
 * Initials in a circle. TFL never loads remote images; photo avatars will be decrypted locally later.
 *
 * @param contentDescription null when the name is already shown next to the avatar.
 */
@Composable
fun TflAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    containerColor: Color = TflTheme.colors.surfaceContainer,
    contentColor: Color = TflTheme.colors.primary,
    badge: AvatarBadge? = null,
    contentDescription: String? = null,
) {
    val colors = TflTheme.colors
    val fontSize = with(LocalDensity.current) { (size * 0.34f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor, CircleShape)
                .border(1.dp, colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials.take(2).uppercase(),
                style = TflTheme.typography.codeMd.copy(fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
            )
        }
        when (badge) {
            is AvatarBadge.Reachable -> Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.32f)
                    .background(colors.canvas, CircleShape)
                    .padding(2.dp)
                    .background(colors.forTransport(badge.transport), CircleShape),
            )
            AvatarBadge.Alert -> Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.38f)
                    .background(colors.canvas, CircleShape)
                    .padding(2.dp)
                    .background(colors.danger, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                TflIcon(MaterialSymbols.PriorityHigh, contentDescription = null, size = size * 0.24f, tint = colors.textPrimary)
            }
            null -> Unit
        }
    }
}

/** Up to two letters or digits for an avatar: the first of each of the first two words ("Kaelen (Valkyrie)" → "KV"). */
fun initialsOf(name: String): String = name
    .split(Regex("[^\\p{L}\\p{N}]+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("") { word -> String(Character.toChars(word.codePointAt(0))).uppercase() }
    .ifEmpty { "?" }
