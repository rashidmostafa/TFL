package app.tfl.feature.chats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.CountBadge
import app.tfl.core.designsystem.component.DeliveryStatusIcon
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TransportChip
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.Transport
import app.tfl.feature.chats.ContactTrust
import app.tfl.feature.chats.ConversationItem
import app.tfl.feature.chats.ConversationKind
import app.tfl.feature.chats.R

private val AvatarColumn = 44.dp
private val ContentIndent = AvatarColumn + 12.dp

@Composable
private fun titleStyle() = TflTheme.typography.headlineSm.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** A direct or group conversation: avatar, name, preview, and a transport footer. */
@Composable
internal fun ConversationCard(
    item: ConversationItem,
    onClick: () -> Unit,
    onResolveKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    val keyChanged = item.trust == ContactTrust.KEY_CHANGED
    val isGroup = item.kind == ConversationKind.GROUP
    TflCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 10.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isGroup) {
                GroupAvatar(item.memberInitials)
            } else {
                TflAvatar(
                    initials = item.initials,
                    badge = when {
                        keyChanged -> AvatarBadge.Alert
                        item.transport == Transport.QUEUED -> null
                        else -> AvatarBadge.Reachable(item.transport)
                    },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = titleStyle(),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TrustIcon(item.trust)
                }
                Text(
                    text = buildAnnotatedString {
                        if (item.previewAuthor != null) {
                            withStyle(SpanStyle(color = colors.mesh)) { append("${item.previewAuthor}: ") }
                        }
                        append(item.preview)
                    },
                    style = TflTheme.typography.bodyMd,
                    color = if (keyChanged) colors.danger else colors.textPrimary.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.time, style = TflTheme.typography.codeSm, color = colors.textMuted)
                when {
                    keyChanged -> Tag(stringResource(R.string.chats_alert), color = colors.danger, solid = true)
                    item.unreadCount > 0 -> UnreadBadge(item.unreadCount, mesh = isGroup)
                    item.lastStatus != null -> DeliveryStatusIcon(item.lastStatus)
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(start = ContentIndent)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportChip(item.transport, detail = item.transportDetail)
            Spacer(Modifier.weight(1f))
            if (keyChanged) {
                PillButton(
                    text = stringResource(R.string.chats_resolve_key),
                    onClick = onResolveKey,
                    trailingIcon = MaterialSymbols.ChevronRight,
                    contentColor = colors.danger,
                    containerColor = colors.danger.copy(alpha = 0.12f),
                )
            } else if (item.routeNote != null) {
                Text(item.routeNote, style = TflTheme.typography.codeSm, color = colors.textMuted, maxLines = 1)
            }
        }
    }
}

/** A pinned broadcast channel: tower tile, channel name, latest bulletin. */
@Composable
internal fun BroadcastCard(
    item: ConversationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    TflCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 8.dp,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconTile(MaterialSymbols.CellTower, size = AvatarColumn)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(10.dp)
                        .background(colors.primary, CircleShape)
                        .border(2.dp, colors.surface, CircleShape),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = titleStyle(),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TrustIcon(item.trust)
                }
                if (item.subtitle != null) {
                    Text(item.subtitle, style = TflTheme.typography.codeSm, color = colors.textMuted, maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.time, style = TflTheme.typography.codeSm, color = colors.textMuted)
                if (item.unreadCount > 0) UnreadBadge(item.unreadCount, mesh = false)
            }
        }
        Text(
            text = item.preview,
            modifier = Modifier.padding(start = ContentIndent),
            style = TflTheme.typography.bodyMd,
            color = colors.textPrimary.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .padding(start = ContentIndent)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportChip(item.transport, detail = item.transportDetail)
            Spacer(Modifier.weight(1f))
            TflIcon(MaterialSymbols.Lock, contentDescription = null, size = 14.dp, tint = colors.textMuted)
            Text(
                text = stringResource(R.string.chats_encrypted),
                modifier = Modifier.padding(start = 4.dp),
                style = TflTheme.typography.codeSm,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun TrustIcon(trust: ContactTrust) {
    val colors = TflTheme.colors
    when (trust) {
        ContactTrust.VERIFIED -> TflIcon(
            MaterialSymbols.Verified,
            contentDescription = stringResource(R.string.chats_trust_verified),
            size = 16.dp,
            tint = colors.primary,
        )
        ContactTrust.UNVERIFIED -> TflIcon(
            MaterialSymbols.GppMaybe,
            contentDescription = stringResource(R.string.chats_trust_unverified),
            size = 16.dp,
            tint = colors.warning,
        )
        ContactTrust.KEY_CHANGED -> TflIcon(
            MaterialSymbols.Warning,
            contentDescription = stringResource(R.string.chats_trust_key_changed),
            size = 16.dp,
            tint = colors.danger,
        )
    }
}

@Composable
private fun UnreadBadge(count: Int, mesh: Boolean) {
    val colors = TflTheme.colors
    val description = pluralStringResource(R.plurals.chats_unread, count, count)
    CountBadge(
        count = count,
        modifier = Modifier.semantics { contentDescription = description },
        color = if (mesh) colors.mesh else colors.primary,
        contentColor = if (mesh) colors.onMesh else colors.onPrimary,
    )
}

/** Two overlapping member avatars for a group. */
@Composable
private fun GroupAvatar(initials: List<String>) {
    val colors = TflTheme.colors
    Box(Modifier.size(AvatarColumn)) {
        TflAvatar(
            initials = initials.getOrElse(0) { "" },
            size = 28.dp,
            containerColor = colors.mesh,
            contentColor = colors.onMesh,
        )
        TflAvatar(
            initials = initials.getOrElse(1) { "" },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .border(2.dp, colors.surface, CircleShape),
            size = 28.dp,
        )
    }
}
