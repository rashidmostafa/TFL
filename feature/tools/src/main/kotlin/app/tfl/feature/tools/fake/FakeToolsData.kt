package app.tfl.feature.tools.fake

import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.feature.tools.ActivityItem
import app.tfl.feature.tools.ToolAccent
import app.tfl.feature.tools.ToolItem
import app.tfl.feature.tools.ToolsUiState

/**
 * PHASE 0 PLACEHOLDER DATA adapted from the Stitch mock. The collaborative tools arrive in Phase 9;
 * nothing here is real.
 */
internal object FakeToolsData {
    val state = ToolsUiState(
        peerCount = 12,
        syncedDocuments = 6,
        collaborators = listOf("MY", "KV", "AL"),
        syncingDocuments = 4,
        tools = listOf(
            ToolItem(
                "notes", "Shared notes", "Notes everyone can edit at once, merged automatically",
                MaterialSymbols.StickyNote2, "3 ACTIVE", "Edited 4m ago", ToolAccent.PRIMARY,
            ),
            ToolItem(
                "todo", "Shared to-do", "Checklists with assignees and due dates",
                MaterialSymbols.CheckBox, "5 OPEN", "8 of 12 done", ToolAccent.MESH,
            ),
            ToolItem(
                "calendar", "Group calendar", "Meetups and plans that work offline",
                MaterialSymbols.EventNote, "NEXT 19:30", "2 events today", ToolAccent.TOR,
            ),
            ToolItem(
                "expenses", "Expense splitter", "Shared costs, settled with the fewest transfers",
                MaterialSymbols.AccountBalanceWallet, "+৳650", "4 friends", ToolAccent.SUCCESS,
            ),
            ToolItem(
                "whiteboard", "Whiteboard", "Sketch plans and routes together",
                MaterialSymbols.Draw, "LIVE", "2 drawing", ToolAccent.MESH,
            ),
            ToolItem(
                "filedrop", "File drop", "Send a file straight to a phone in range",
                MaterialSymbols.WifiTethering, "NEARBY", "No internet needed", ToolAccent.PRIMARY,
            ),
        ),
        activity = listOf(
            ActivityItem("plan", "Ridge Point plan", "Shared note · Maya · 4m ago", MaterialSymbols.StickyNote2, "LIVE", ToolAccent.PRIMARY),
            ActivityItem("supplies", "Supply checklist", "To-do · 8/12 done · Kaelen", MaterialSymbols.Checklist, "SYNCED", ToolAccent.MESH),
            ActivityItem("meetup", "Weekend meetup", "Calendar · Tomorrow 14:00", MaterialSymbols.EventRepeat, "SCHEDULED", ToolAccent.TOR),
        ),
    )
}
