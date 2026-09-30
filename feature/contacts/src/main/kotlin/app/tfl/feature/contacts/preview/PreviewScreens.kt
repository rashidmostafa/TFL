package app.tfl.feature.contacts.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflProgressBar
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.components.initialsOf
import app.tfl.feature.contacts.fake.FakeContactsData
import app.tfl.feature.contacts.fake.GroupJoinPreview
import app.tfl.feature.contacts.fake.PreviewApproval
import app.tfl.feature.contacts.fake.RemoteWipePreview

/** What approving a new group member will look like. Sample data; the actions do nothing yet. */
@Composable
internal fun GroupJoinPreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preview: GroupJoinPreview = FakeContactsData.groupJoin,
) = PreviewScaffold(stringResource(R.string.group_join_title), onBack, modifier) {
    Callout(stringResource(R.string.group_join_explainer), title = stringResource(R.string.group_join_explainer_title))
    TflCard(verticalSpacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TflAvatar(initialsOf(preview.newMember), size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(preview.newMember, style = TflTheme.typography.headlineSm, color = TflTheme.colors.textPrimary)
                Text(
                    stringResource(R.string.group_join_wants_to_join, preview.groupName),
                    style = TflTheme.typography.bodySm,
                    color = TflTheme.colors.textMuted,
                )
            }
            Tag(stringResource(R.string.group_join_new_tag))
        }
        FingerprintBlock(preview.fingerprint, label = stringResource(R.string.group_join_fingerprint))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Fact(stringResource(R.string.group_join_invited_by), preview.invitedBy, Modifier.weight(1f))
            Fact(
                stringResource(R.string.group_join_received),
                pluralStringResource(R.plurals.preview_minutes_ago, preview.receivedMinutesAgo, preview.receivedMinutesAgo),
                Modifier.weight(1f),
            )
        }
    }
    Approvals(preview.approvals)
    PrimaryButton(stringResource(R.string.group_join_approve), onClick = {}, enabled = false, icon = MaterialSymbols.HowToReg, modifier = Modifier.fillMaxWidth())
    DestructiveButton(stringResource(R.string.group_join_reject), onClick = {}, enabled = false, icon = MaterialSymbols.Block, modifier = Modifier.fillMaxWidth())
}

/** What asking friends to wipe a lost phone will look like. Sample data; the actions do nothing yet. */
@Composable
internal fun RemoteWipePreviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    preview: RemoteWipePreview = FakeContactsData.remoteWipe,
) = PreviewScaffold(stringResource(R.string.remote_wipe_title), onBack, modifier) {
    Callout(
        stringResource(R.string.remote_wipe_explainer),
        title = stringResource(R.string.remote_wipe_explainer_title),
        tone = CalloutTone.Danger,
    )
    TflCard(verticalSpacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TflAvatar(initialsOf(preview.owner), size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.remote_wipe_device, preview.owner),
                    style = TflTheme.typography.headlineSm,
                    color = TflTheme.colors.textPrimary,
                )
                Text(
                    pluralStringResource(R.plurals.remote_wipe_last_seen, preview.lastSeenHoursAgo, preview.lastSeenHoursAgo),
                    style = TflTheme.typography.bodySm,
                    color = TflTheme.colors.textMuted,
                )
            }
            Tag(stringResource(R.string.remote_wipe_lost_tag), color = TflTheme.colors.danger)
        }
        FingerprintBlock(preview.fingerprint, label = stringResource(R.string.remote_wipe_fingerprint))
    }
    Approvals(preview.approvals)
    DestructiveButton(
        stringResource(R.string.remote_wipe_send),
        onClick = {},
        enabled = false,
        icon = MaterialSymbols.PowerSettingsNew,
        modifier = Modifier.fillMaxWidth(),
    )
    GhostButton(stringResource(R.string.remote_wipe_cancel), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PreviewScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        TflTopBar(title = title, navigationIcon = { TflBackButton(onClick = onBack) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Callout(
                stringResource(R.string.preview_banner_body),
                title = stringResource(R.string.preview_banner_title),
                tone = CalloutTone.Warning,
            )
            content()
        }
    }
}

@Composable
private fun Approvals(approvals: List<PreviewApproval>) {
    val colors = TflTheme.colors
    val approved = approvals.count { it.approved }
    TflCard(verticalSpacing = 12.dp) {
        SectionHeader(
            stringResource(R.string.preview_approvals),
            icon = MaterialSymbols.HowToVote,
            trailing = stringResource(R.string.preview_approvals_count, approved, approvals.size),
        )
        TflProgressBar(progress = approved / approvals.size.toFloat())
        approvals.forEach { approval ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TflAvatar(initialsOf(approval.name), size = 36.dp)
                Text(
                    if (approval.isYou) stringResource(R.string.preview_you) else approval.name,
                    modifier = Modifier.weight(1f),
                    style = TflTheme.typography.bodyMd,
                    color = colors.textPrimary,
                )
                if (approval.approved) {
                    TflIcon(MaterialSymbols.CheckCircle, contentDescription = stringResource(R.string.preview_approved), size = 22.dp, tint = colors.primary)
                } else {
                    TflIcon(MaterialSymbols.HourglassTop, contentDescription = stringResource(R.string.preview_waiting), size = 22.dp, tint = colors.warning)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = TflTheme.typography.labelSm, color = TflTheme.colors.textMuted)
        Text(value, style = TflTheme.typography.bodyMd, color = TflTheme.colors.textPrimary)
    }
}
