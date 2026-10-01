package app.tfl.feature.contacts.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.ListRowTone
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SafetyNumberGrid
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflTextField
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.identity.DisplayNames
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.TrustBadge
import app.tfl.feature.contacts.components.formatDate
import app.tfl.feature.contacts.components.keySourceLabel
import app.tfl.feature.contacts.components.verifiedByLabel
import app.tfl.feature.contacts.debug.ContactsDebugTools

/** What a profile can open. */
internal class ProfileActions(
    val onBack: () -> Unit,
    val onCompare: () -> Unit,
    val onReviewKeyChange: () -> Unit,
    val onEditNickname: () -> Unit,
    val onSetBlocked: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onMessage: () -> Unit = {},
)

@Composable
internal fun ContactProfileScreen(
    onBack: () -> Unit,
    onMessage: (contactId: Long) -> Unit,
    onCompare: (contactId: Long) -> Unit,
    onReviewKeyChange: (contactId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(viewModel) { viewModel.closed.collect { currentOnBack() } }
    var editingNickname by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val friend = uiState.friend

    ContactProfileContent(
        uiState = uiState,
        actions = ProfileActions(
            onBack = onBack,
            onCompare = { friend?.let { onCompare(it.id) } },
            onReviewKeyChange = { friend?.let { onReviewKeyChange(it.id) } },
            onEditNickname = { editingNickname = true },
            onSetBlocked = viewModel::setBlocked,
            onDelete = { confirmingDelete = true },
            onMessage = { friend?.let { onMessage(it.id) } },
        ),
        modifier = modifier,
        debugTools = { id -> ContactsDebugTools.ProfileTools(id, onKeyChanged = { onReviewKeyChange(id) }) },
    )
    if (friend != null && editingNickname) {
        NicknameSheet(
            current = friend.nickname,
            theirName = friend.displayName,
            onSave = {
                viewModel.setNickname(it)
                editingNickname = false
            },
            onDismiss = { editingNickname = false },
        )
    }
    if (friend != null && confirmingDelete) {
        DeleteSheet(
            name = friend.name,
            onDelete = {
                confirmingDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
internal fun ContactProfileContent(
    uiState: FriendUiState,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
    debugTools: @Composable (contactId: Long) -> Unit = {},
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.profile_title),
            navigationIcon = { TflBackButton(onClick = actions.onBack) },
        )
        val friend = uiState.friend ?: return@Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(friend)
            PrimaryButton(
                stringResource(R.string.profile_message),
                onClick = actions.onMessage,
                icon = MaterialSymbols.Forum,
                modifier = Modifier.fillMaxWidth(),
            )
            if (friend.trust == Trust.KEY_CHANGED) {
                Callout(
                    text = stringResource(R.string.profile_key_changed_body, friend.name, formatDate(friend.keyChangedAtMillis ?: 0)),
                    title = stringResource(R.string.profile_key_changed_title),
                    tone = CalloutTone.Danger,
                )
                DestructiveButton(
                    stringResource(R.string.profile_review_key_change),
                    onClick = actions.onReviewKeyChange,
                    icon = MaterialSymbols.GppBad,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (friend.blocked) {
                Callout(stringResource(R.string.profile_blocked_body, friend.name), tone = CalloutTone.Warning)
            }
            SafetyNumberCard(friend, uiState.safetyNumber, actions.onCompare)
            KeysCard(friend, uiState.history)
            ListRow(
                title = stringResource(R.string.profile_nickname),
                subtitle = friend.nickname ?: stringResource(R.string.profile_nickname_none),
                icon = MaterialSymbols.Edit,
                onClick = actions.onEditNickname,
            )
            debugTools(friend.id)
            TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                ListRow(
                    title = stringResource(if (friend.blocked) R.string.profile_unblock else R.string.profile_block, friend.name),
                    subtitle = stringResource(if (friend.blocked) R.string.profile_unblock_subtitle else R.string.profile_block_subtitle),
                    icon = MaterialSymbols.Block,
                    standalone = false,
                    trailing = null,
                    onClick = { actions.onSetBlocked(!friend.blocked) },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.profile_delete),
                    subtitle = stringResource(R.string.profile_delete_subtitle),
                    icon = MaterialSymbols.DeleteForever,
                    tone = ListRowTone.Danger,
                    standalone = false,
                    trailing = null,
                    onClick = actions.onDelete,
                )
            }
        }
    }
}

@Composable
private fun Header(friend: FriendDetails) {
    val colors = TflTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TflAvatar(
            friend.initials,
            size = 88.dp,
            badge = if (friend.trust == Trust.KEY_CHANGED) AvatarBadge.Alert else null,
        )
        Text(friend.name, style = TflTheme.typography.headlineMd, color = colors.textPrimary, textAlign = TextAlign.Center)
        if (friend.nickname != null) {
            Text(
                stringResource(R.string.profile_their_name, friend.displayName),
                style = TflTheme.typography.bodyMd,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        TrustBadge(friend.trust)
    }
}

@Composable
private fun SafetyNumberCard(friend: FriendDetails, safetyNumber: List<String>, onCompare: () -> Unit) {
    val colors = TflTheme.colors
    TflCard(verticalSpacing = 12.dp) {
        SectionHeader(stringResource(R.string.profile_safety_number), icon = MaterialSymbols.Fingerprint)
        SafetyNumberGrid(safetyNumber)
        Text(
            text = verificationLine(friend),
            style = TflTheme.typography.bodySm,
            color = if (friend.trust == Trust.VERIFIED) colors.primary else colors.textMuted,
        )
        PrimaryButton(
            stringResource(R.string.profile_compare),
            onClick = onCompare,
            icon = MaterialSymbols.CompareArrows,
            size = TflButtonSize.Medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun verificationLine(friend: FriendDetails): String {
    val method = friend.verifiedBy
    val at = friend.verifiedAtMillis
    return if (friend.trust == Trust.VERIFIED && method != null && at != null) {
        stringResource(R.string.profile_verified_line, verifiedByLabel(method), formatDate(at))
    } else {
        stringResource(R.string.profile_unverified_line)
    }
}

@Composable
private fun KeysCard(friend: FriendDetails, history: List<PreviousKeyItem>) {
    TflCard(verticalSpacing = 10.dp) {
        SectionHeader(stringResource(R.string.profile_keys), icon = MaterialSymbols.VpnKey)
        FactRow(stringResource(R.string.profile_identity_key), stringResource(R.string.profile_identity_key_value))
        FactRow(stringResource(R.string.profile_kex_key), stringResource(R.string.profile_kex_key_value))
        FactRow(stringResource(R.string.profile_first_seen), formatDate(friend.firstSeenAtMillis))
        FactRow(
            stringResource(R.string.profile_key_since),
            stringResource(R.string.profile_key_since_value, formatDate(friend.keySinceMillis), keySourceLabel(friend.keySource)),
        )
        FingerprintBlock(friend.fingerprint, label = stringResource(R.string.profile_fingerprint))
        if (history.isNotEmpty()) {
            SectionHeader(
                stringResource(R.string.profile_previous_keys),
                modifier = Modifier.padding(top = 8.dp),
                icon = MaterialSymbols.History,
                iconTint = TflTheme.colors.textMuted,
            )
            history.forEach { key ->
                FingerprintBlock(
                    key.fingerprint,
                    label = stringResource(
                        R.string.profile_previous_key_used,
                        formatDate(key.firstSeenAtMillis),
                        formatDate(key.replacedAtMillis),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun FactRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = TflTheme.typography.bodyMd, color = TflTheme.colors.textMuted)
        Text(value, style = TflTheme.typography.codeMd, color = TflTheme.colors.textPrimary, textAlign = TextAlign.End)
    }
}

@Composable
private fun NicknameSheet(current: String?, theirName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val field = rememberTextFieldState(current.orEmpty())
    val text = field.text.trim().toString()
    val valid = text.isEmpty() || DisplayNames.isValid(text)
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.profile_nickname_title), icon = MaterialSymbols.Edit)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.profile_nickname_body, theirName),
                style = TflTheme.typography.bodyMd,
                color = TflTheme.colors.textMuted,
            )
            TflTextField(
                field,
                placeholder = theirName,
                supportingText = if (valid) null else stringResource(R.string.profile_nickname_error),
                isError = !valid,
            )
            PrimaryButton(
                stringResource(R.string.profile_nickname_save),
                onClick = { onSave(text) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(stringResource(R.string.profile_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DeleteSheet(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.profile_delete_title, name), icon = MaterialSymbols.DeleteForever)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.profile_delete_body, name),
                style = TflTheme.typography.bodyMd,
                color = TflTheme.colors.textMuted,
            )
            DestructiveButton(stringResource(R.string.profile_delete_confirm), onClick = onDelete, modifier = Modifier.fillMaxWidth())
            GhostButton(stringResource(R.string.profile_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
