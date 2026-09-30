package app.tfl.feature.contacts.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.glow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.TrustBadge

@Composable
internal fun FriendAddedScreen(
    onDone: () -> Unit,
    onViewProfile: (contactId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FriendAddedContent(uiState, onDone, onViewProfile = { uiState.friend?.let { onViewProfile(it.id) } }, modifier)
}

@Composable
internal fun FriendAddedContent(
    uiState: FriendUiState,
    onDone: () -> Unit,
    onViewProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    val friend = uiState.friend
    val verified = friend?.trust == Trust.VERIFIED
    val accent = if (verified) colors.primary else colors.warning
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TflIconButton(MaterialSymbols.Close, stringResource(R.string.friend_added_close), onClick = onDone)
        }
        if (friend == null) return@Column
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(112.dp)
                    .glow(accent, CircleShape, radius = 28.dp, alpha = 0.25f)
                    .border(6.dp, accent, CircleShape)
                    .padding(6.dp)
                    .background(colors.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                TflIcon(if (verified) MaterialSymbols.VerifiedUser else MaterialSymbols.GppMaybe, contentDescription = null, size = 48.dp, tint = accent, filled = true)
            }
            Text(
                stringResource(if (verified) R.string.friend_added_verified_title else R.string.friend_added_unverified_title),
                style = TflTheme.typography.headlineLgMobile,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (verified) R.string.friend_added_verified_body else R.string.friend_added_unverified_body, friend.name),
                style = TflTheme.typography.bodyMd,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        TflCard(verticalSpacing = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TflAvatar(friend.initials, size = 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(friend.name, style = TflTheme.typography.headlineSm, color = colors.textPrimary)
                    TrustBadge(friend.trust)
                }
            }
            FingerprintBlock(friend.fingerprint, label = stringResource(R.string.friend_added_fingerprint))
            FactRow(
                stringResource(R.string.friend_added_method),
                stringResource(if (verified) R.string.friend_added_method_both else R.string.friend_added_method_one),
            )
        }
        Callout(stringResource(R.string.friend_added_stored))
        PrimaryButton(stringResource(R.string.friend_added_view_profile), onClick = onViewProfile, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(R.string.friend_added_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
