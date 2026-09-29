package app.tfl.feature.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.TransportChip
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.map.FriendLocation
import app.tfl.feature.map.R

/** A friend sharing their location: distance, time left, battery, route, and a navigate action. */
@Composable
internal fun FriendCard(
    friend: FriendLocation,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    TflCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalSpacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TflAvatar(friend.initials, size = 48.dp, badge = AvatarBadge.Reachable(friend.transport))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = friend.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = TflTheme.typography.headlineSm.copy(fontSize = 16.sp),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (friend.verified) Tag(stringResource(R.string.map_verified))
                }
                Text(
                    text = "${friend.distance} · ${friend.place}",
                    style = TflTheme.typography.bodyMd,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(friend.timeLeft, style = TflTheme.typography.codeMd, color = colors.primary)
                BatteryLevel(friend.batteryPercent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TransportChip(friend.transport, detail = friend.transportDetail)
            if (friend.updated != null) {
                Text(
                    text = friend.updated,
                    modifier = Modifier.weight(1f, fill = false),
                    style = TflTheme.typography.codeSm,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            TflIconButton(
                symbol = MaterialSymbols.NearMe,
                contentDescription = stringResource(R.string.map_navigate, friend.name),
                onClick = onNavigate,
                tint = colors.primary,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }
    }
}

@Composable
private fun BatteryLevel(percent: Int) {
    val colors = TflTheme.colors
    val description = stringResource(R.string.map_battery, percent)
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TflIcon(
            symbol = when {
                percent >= 80 -> MaterialSymbols.Battery6Bar
                percent >= 50 -> MaterialSymbols.Battery5Bar
                else -> MaterialSymbols.Battery3Bar
            },
            contentDescription = null,
            size = 14.dp,
            tint = if (percent >= 50) colors.primary else colors.warning,
        )
        Text("$percent%", style = TflTheme.typography.codeSm, color = colors.textMuted)
    }
}
