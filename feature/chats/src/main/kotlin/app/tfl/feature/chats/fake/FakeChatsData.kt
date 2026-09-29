package app.tfl.feature.chats.fake

import app.tfl.core.model.DeliveryStatus
import app.tfl.core.model.Transport
import app.tfl.feature.chats.ContactTrust
import app.tfl.feature.chats.ConversationItem
import app.tfl.feature.chats.ConversationKind

/**
 * PHASE 0 PLACEHOLDER DATA. Names and messages are mock content adapted from the Stitch designs.
 * Replaced by the encrypted conversation repository in Phase 3; nothing here is real.
 */
internal object FakeChatsData {
    const val PEER_COUNT = 4
    const val TOR_CONNECTED = true

    val conversations = listOf(
        ConversationItem(
            id = "bulletins",
            kind = ConversationKind.BROADCAST,
            title = "Emergency bulletins",
            initials = "EB",
            subtitle = "BROADCAST · 8 PEERS",
            preview = "Storm cell incoming. Keep relays powered and charge your battery packs.",
            time = "14:20",
            unreadCount = 1,
            transport = Transport.MESH,
            transportDetail = "2 hops",
        ),
        ConversationItem(
            id = "sector7",
            kind = ConversationKind.GROUP,
            title = "Sector 7 Friends",
            initials = "S7",
            memberInitials = listOf("AL", "MY"),
            previewAuthor = "Alex",
            preview = "Checking in at Ridge Point",
            time = "14:15",
            unreadCount = 3,
            transport = Transport.MESH,
            transportDetail = "3 hops",
            routeNote = "Synced",
        ),
        ConversationItem(
            id = "kaelen",
            kind = ConversationKind.DIRECT,
            title = "Kaelen (Valkyrie)",
            initials = "KV",
            preview = "Audio memo received, listening now.",
            time = "Just now",
            transport = Transport.NEARBY,
            transportDetail = "Direct",
            lastStatus = DeliveryStatus.READ,
        ),
        ConversationItem(
            id = "maya",
            kind = ConversationKind.DIRECT,
            title = "Dr. Maya Lin",
            initials = "ML",
            preview = "Made it to the lookout. Signal is weak up here.",
            time = "12m ago",
            transport = Transport.MESH,
            transportDetail = "2 hops",
            lastStatus = DeliveryStatus.DELIVERED,
            routeNote = "Via 1 relay",
        ),
        ConversationItem(
            id = "soren",
            kind = ConversationKind.DIRECT,
            title = "Soren K.",
            initials = "SK",
            preview = "You sent a file (3.2 MB)",
            time = "1h ago",
            transport = Transport.TOR,
            lastStatus = DeliveryStatus.SENT,
            routeNote = "Waiting for Soren",
        ),
        ConversationItem(
            id = "elena",
            kind = ConversationKind.DIRECT,
            title = "Elena Vance",
            initials = "EV",
            preview = "New identity key detected. Messages are paused until you verify.",
            time = "3h ago",
            trust = ContactTrust.KEY_CHANGED,
            transport = Transport.QUEUED,
            transportDetail = "Unverified",
        ),
    )
}
