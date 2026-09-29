package app.tfl.core.designsystem.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.AvatarBadge
import app.tfl.core.designsystem.component.BarSegment
import app.tfl.core.designsystem.component.BubbleDirection
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.CountBadge
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.EmptyState
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.ListRowTone
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.ProfileButton
import app.tfl.core.designsystem.component.RadioRow
import app.tfl.core.designsystem.component.SecureBadge
import app.tfl.core.designsystem.component.SecureBadgeState
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SegmentedBar
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.StatusLine
import app.tfl.core.designsystem.component.StatusPill
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TextBubble
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflAvatar
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflFilterChip
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.component.TflLogo
import app.tfl.core.designsystem.component.TflNavigationBar
import app.tfl.core.designsystem.component.TflNavigationBarItem
import app.tfl.core.designsystem.component.TflProgressBar
import app.tfl.core.designsystem.component.TflRadioButton
import app.tfl.core.designsystem.component.TflSearchField
import app.tfl.core.designsystem.component.TflSheetDefaults
import app.tfl.core.designsystem.component.TflSwitch
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.component.TransportChip
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport

/*
 * Every shared component in representative states. Shown by the debug-only design catalog screen
 * and captured group by group in :core:designsystem's screenshot tests. Sample text only.
 */

/** A catalog group: section header plus its samples. */
class CatalogSection(val title: String, val content: @Composable () -> Unit)

val catalogSections: List<CatalogSection> = listOf(
    CatalogSection("Chips & badges") { CatalogChipsAndBadges() },
    CatalogSection("Buttons") { CatalogButtons() },
    CatalogSection("List rows") { CatalogListRows() },
    CatalogSection("Message bubbles") { CatalogBubbles() },
    CatalogSection("Avatars") { CatalogAvatars() },
    CatalogSection("Bars & inputs") { CatalogBarsAndInputs() },
    CatalogSection("Callouts, keys & progress") { CatalogCalloutsAndKeys() },
    CatalogSection("Sheet header") { CatalogSheetHeader() },
    CatalogSection("Empty state") { CatalogEmptyState() },
)

@Composable
private fun Samples(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogChipsAndBadges() = Samples {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TransportChip(Transport.NEARBY, detail = "Direct")
        TransportChip(Transport.MESH, detail = "2 hops")
        TransportChip(Transport.TOR)
        TransportChip(Transport.QUEUED, detail = "Unverified")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecureBadge()
        SecureBadge(state = SecureBadgeState.Unverified)
        SecureBadge(state = SecureBadgeState.KeyChanged)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill("MESH · 12 PEERS")
        StatusPill("TOR ON", color = TflTheme.colors.tor)
        Tag("VERIFIED")
        Tag("ALERT", color = TflTheme.colors.danger, solid = true)
        CountBadge(3)
        CountBadge(12, color = TflTheme.colors.mesh, contentColor = TflTheme.colors.onMesh)
    }
}

@Composable
fun CatalogButtons() = Samples {
    PrimaryButton("Share my live location", onClick = {}, icon = MaterialSymbols.ShareLocation, glow = true, modifier = Modifier.fillMaxWidth())
    GhostButton("Save QR to vault", onClick = {}, icon = MaterialSymbols.Folder, modifier = Modifier.fillMaxWidth())
    DestructiveButton("Lock vault now", onClick = {}, icon = MaterialSymbols.PowerSettingsNew, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton("Grant", onClick = {}, size = TflButtonSize.Medium)
        GhostButton("Later", onClick = {}, size = TflButtonSize.Medium)
        PrimaryButton("Disabled", onClick = {}, size = TflButtonSize.Medium, enabled = false)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("SCAN", onClick = {}, icon = MaterialSymbols.QrCodeScanner)
        PillButton("Split", onClick = {}, trailingIcon = MaterialSymbols.ArrowForward, contentColor = TflTheme.colors.textPrimary)
        TflIconButton(MaterialSymbols.Search, "Search", onClick = {})
        ProfileButton(onClick = {})
    }
}

@Composable
fun CatalogListRows() = Samples {
    ListRow(
        title = "Security",
        subtitle = "Biometrics, duress PIN, panic wipe, app lock",
        icon = MaterialSymbols.Security,
        titleBadge = { Tag("NEW") },
        onClick = {},
    )
    ListRow(
        title = "Network & transports",
        subtitle = "Tor onion service, Nearby, mesh relay",
        icon = MaterialSymbols.CellTower,
        onClick = {},
    )
    TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
        ToggleRow(title = "App lock", subtitle = "PIN and biometrics", checked = true, onCheckedChange = {})
        TflDivider()
        ListRow(title = "Screenshot blocking", subtitle = "FLAG_SECURE", standalone = false, trailing = { Tag("ALWAYS ON") })
        TflDivider()
        RadioRow(title = "Shake five times", selected = true, onSelect = {})
    }
    ListRow(
        title = "Emergency duress / panic wipe",
        subtitle = "Immediate key destruction and decoy identity",
        icon = MaterialSymbols.Warning,
        tone = ListRowTone.Danger,
        onClick = {},
    )
}

@Composable
fun CatalogBubbles() = Samples {
    TextBubble(
        text = "Are you within Bluetooth range of the water tower?",
        direction = BubbleDirection.Incoming,
        time = "14:02",
        transport = Transport.NEARBY,
        modifier = Modifier.fillMaxWidth(0.82f),
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextBubble("Yes, connected directly.", BubbleDirection.Outgoing, "14:05", transport = Transport.NEARBY, status = DeliveryStatus.READ)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextBubble("Sending the updated route table", BubbleDirection.Outgoing, "14:08", transport = Transport.MESH, status = DeliveryStatus.DELIVERED)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextBubble("Waiting for a route", BubbleDirection.Outgoing, "14:09", status = DeliveryStatus.QUEUED)
    }
}

@Composable
fun CatalogAvatars() = Samples {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        TflAvatar("KV", badge = AvatarBadge.Reachable(Transport.NEARBY))
        TflAvatar("ML", badge = AvatarBadge.Reachable(Transport.MESH))
        TflAvatar("SK", badge = AvatarBadge.Reachable(Transport.TOR))
        TflAvatar("EV", badge = AvatarBadge.Alert)
        TflAvatar("AL", containerColor = TflTheme.colors.mesh, contentColor = TflTheme.colors.onMesh)
        TflAvatar("V7", size = 64.dp, badge = AvatarBadge.Reachable(Transport.NEARBY))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(MaterialSymbols.PhotoLibrary)
        IconTile(MaterialSymbols.Hub, tint = TflTheme.colors.mesh)
        IconTile(MaterialSymbols.VisibilityOff, tint = TflTheme.colors.danger, containerColor = TflTheme.colors.danger.copy(alpha = 0.15f))
    }
}

@Composable
fun CatalogBarsAndInputs() = Samples {
    TflTopBar(
        title = "Chats",
        subtitle = { StatusLine("4 peers · Tor on") },
        navigationIcon = { TflLogo() },
        actions = { ProfileButton(onClick = {}) },
    )
    TflSearchField(rememberTextFieldState(), placeholder = "Search peers, channels, messages…")
    TflSearchField(rememberTextFieldState("Maya"), placeholder = "")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TflFilterChip("All", selected = true, onClick = {}, count = 7)
        TflFilterChip("Direct", selected = false, onClick = {}, icon = MaterialSymbols.Lock, count = 3)
        TflFilterChip("Groups", selected = false, onClick = {}, icon = MaterialSymbols.Hub, count = 2)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        TflSwitch(checked = true, onCheckedChange = {})
        TflSwitch(checked = false, onCheckedChange = {})
        TflSwitch(checked = true, onCheckedChange = null, enabled = false)
        TflRadioButton(selected = true, onClick = {})
        TflRadioButton(selected = false, onClick = {})
    }
    TflNavigationBar {
        TflNavigationBarItem(true, {}, MaterialSymbols.ChatBubble, "Chats")
        TflNavigationBarItem(false, {}, MaterialSymbols.Hub, "Map")
        TflNavigationBarItem(false, {}, MaterialSymbols.Lock, "Vault")
        TflNavigationBarItem(false, {}, MaterialSymbols.Terminal, "Tools")
        TflNavigationBarItem(false, {}, MaterialSymbols.Settings, "Settings")
    }
}

@Composable
fun CatalogCalloutsAndKeys() = Samples {
    Callout("Exchange keys in person. Never trust a fingerprint sent over another channel.")
    Callout("These settings aren't saved or enforced yet.", title = "Preview", tone = CalloutTone.Warning)
    Callout("Elena's key changed. Messages to her are paused until you verify again.", tone = CalloutTone.Danger)
    FingerprintBlock(listOf("7F4B", "889C", "20AE", "99C2"), label = "Public key fingerprint", onCopy = {})
    SectionHeader("Active threads", icon = MaterialSymbols.Forum, iconTint = TflTheme.colors.mesh, trailing = "E2EE")
    TflProgressBar(progress = 0.57f)
    val colors = TflTheme.colors
    SegmentedBar(
        listOf(
            BarSegment(14.2f, colors.primary),
            BarSegment(5.1f, colors.mesh),
            BarSegment(0.6f, colors.tor),
            BarSegment(4.9f, colors.success),
            BarSegment(103.2f, Color.Transparent),
        ),
    )
}

@Composable
fun CatalogSheetHeader() = Samples {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TflSheetDefaults.DragHandle()
        SheetHeader(
            title = "Disappearing messages",
            subtitle = "Per-chat timer",
            icon = MaterialSymbols.LocalFireDepartment,
            onClose = {},
        )
    }
}

@Composable
fun CatalogEmptyState() = Samples {
    EmptyState(
        icon = MaterialSymbols.Forum,
        title = "No chats yet",
        message = "Friends are added in person by scanning each other's QR code.",
        action = { GhostButton("Add friend", onClick = {}, icon = MaterialSymbols.QrCodeScanner, size = TflButtonSize.Medium) },
    )
}
