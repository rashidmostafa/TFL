package app.tfl.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

enum class SecureBadgeState {
    /** Keys verified in person; end-to-end encrypted. */
    Verified,

    /** Encrypted, but the key has not been verified in person yet. */
    Unverified,

    /** The contact's key no longer matches the one you verified. */
    KeyChanged,
}

/**
 * Encryption and verification status of a conversation or contact.
 *
 * @param label overrides the default text ("E2EE", "Unverified", "Key changed"). Keep it true to what
 *   the code actually does; never name a cipher the app doesn't use.
 */
@Composable
fun SecureBadge(
    modifier: Modifier = Modifier,
    state: SecureBadgeState = SecureBadgeState.Verified,
    label: String? = null,
) {
    val colors = TflTheme.colors
    val color = when (state) {
        SecureBadgeState.Verified -> colors.primary
        SecureBadgeState.Unverified -> colors.warning
        SecureBadgeState.KeyChanged -> colors.danger
    }
    val symbol = when (state) {
        SecureBadgeState.Verified -> MaterialSymbols.VerifiedUser
        SecureBadgeState.Unverified -> MaterialSymbols.GppMaybe
        SecureBadgeState.KeyChanged -> MaterialSymbols.GppBad
    }
    val text = label ?: stringResource(
        when (state) {
            SecureBadgeState.Verified -> R.string.secure_badge_e2ee
            SecureBadgeState.Unverified -> R.string.secure_badge_unverified
            SecureBadgeState.KeyChanged -> R.string.secure_badge_key_changed
        },
    )
    Pill(
        containerColor = color.copy(alpha = 0.12f),
        borderColor = color.copy(alpha = 0.6f),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        TflIcon(symbol, contentDescription = null, size = 14.dp, tint = color, filled = true)
        Text(text, style = TflTheme.typography.labelSm, color = color, maxLines = 1)
    }
}
