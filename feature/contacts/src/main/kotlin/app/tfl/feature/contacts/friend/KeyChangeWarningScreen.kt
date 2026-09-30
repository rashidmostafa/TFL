package app.tfl.feature.contacts.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.formatDate
import app.tfl.feature.contacts.components.formatDateTime
import app.tfl.feature.contacts.components.keySourceLabel

@Composable
internal fun KeyChangeWarningScreen(
    onBack: () -> Unit,
    onVerifyAgain: (contactId: Long) -> Unit,
    onContinue: (contactId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val id = uiState.friend?.id
    KeyChangeWarningContent(
        uiState = uiState,
        onBack = onBack,
        onVerifyAgain = { id?.let(onVerifyAgain) },
        onBlock = {
            viewModel.setBlocked(true)
            id?.let(onContinue)
        },
        onNotNow = { id?.let(onContinue) },
        modifier = modifier,
    )
}

/**
 * A friend's identity key changed. Sending to them stays paused until the new key is verified,
 * in person or by safety number; there is deliberately no "trust it anyway".
 */
@Composable
internal fun KeyChangeWarningContent(
    uiState: FriendUiState,
    onBack: () -> Unit,
    onVerifyAgain: () -> Unit,
    onBlock: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.key_change_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        val friend = uiState.friend ?: return@Column
        val resolved = friend.trust != Trust.KEY_CHANGED
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (resolved) {
                Callout(stringResource(R.string.key_change_resolved, friend.name), tone = CalloutTone.Success)
            }
            TflCard(borderColor = colors.danger.copy(alpha = 0.4f), verticalSpacing = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(MaterialSymbols.KeyOff, tint = colors.danger, containerColor = colors.danger.copy(alpha = 0.15f), size = 48.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Tag(stringResource(R.string.key_change_tag), color = colors.danger)
                        Text(
                            stringResource(R.string.key_change_heading, friend.name),
                            style = TflTheme.typography.headlineSm,
                            color = colors.textPrimary,
                        )
                    }
                }
                Text(
                    stringResource(R.string.key_change_body, friend.name),
                    style = TflTheme.typography.bodyMd,
                    color = colors.textMuted,
                )
                if (!resolved) {
                    Callout(stringResource(R.string.key_change_paused, friend.name), tone = CalloutTone.Danger)
                }
            }
            TflCard(verticalSpacing = 10.dp) {
                SectionHeader(stringResource(R.string.key_change_keys), icon = MaterialSymbols.Fingerprint)
                uiState.history.firstOrNull()?.let { previous ->
                    KeyBlock(
                        title = stringResource(R.string.key_change_previous),
                        fingerprint = previous.fingerprint,
                        detail = stringResource(R.string.key_change_previous_detail, formatDate(previous.firstSeenAtMillis), formatDate(previous.replacedAtMillis)),
                    )
                }
                KeyBlock(
                    title = stringResource(if (resolved) R.string.key_change_current else R.string.key_change_new),
                    fingerprint = friend.fingerprint,
                    detail = stringResource(
                        R.string.key_change_new_detail,
                        formatDateTime(friend.keySinceMillis),
                        keySourceLabel(friend.keySource),
                    ),
                    danger = !resolved,
                )
            }
            if (!resolved) {
                TflCard(verticalSpacing = 8.dp) {
                    SectionHeader(stringResource(R.string.key_change_steps_title), icon = MaterialSymbols.Checklist)
                    listOf(
                        stringResource(R.string.key_change_step_1, friend.name),
                        stringResource(R.string.key_change_step_2),
                        stringResource(R.string.key_change_step_3),
                    ).forEachIndexed { index, step ->
                        Text("${index + 1}. $step", style = TflTheme.typography.bodyMd, color = colors.textMuted)
                    }
                }
                PrimaryButton(
                    stringResource(R.string.key_change_verify_again),
                    onClick = onVerifyAgain,
                    icon = MaterialSymbols.CompareArrows,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!friend.blocked) {
                    DestructiveButton(
                        stringResource(R.string.key_change_block, friend.name),
                        onClick = onBlock,
                        icon = MaterialSymbols.Block,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            GhostButton(
                stringResource(if (resolved) R.string.key_change_done else R.string.key_change_not_now),
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KeyBlock(title: String, fingerprint: List<String>, detail: String, danger: Boolean = false) {
    val colors = TflTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = TflTheme.typography.labelSm, color = if (danger) colors.danger else colors.primary)
        FingerprintBlock(fingerprint)
        Text(detail, style = TflTheme.typography.bodySm, color = colors.textMuted)
    }
}
