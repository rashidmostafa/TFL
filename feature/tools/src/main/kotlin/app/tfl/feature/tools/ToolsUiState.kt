package app.tfl.feature.tools

import androidx.compose.runtime.Immutable

/** Colour role of a tool or activity; mapped to theme colours in the UI. */
enum class ToolAccent { PRIMARY, MESH, TOR, SUCCESS }

@Immutable
data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val badge: String,
    val footer: String,
    val accent: ToolAccent,
)

@Immutable
data class ActivityItem(
    val id: String,
    val title: String,
    val details: String,
    val icon: String,
    val status: String,
    val accent: ToolAccent,
)

@Immutable
data class ToolsUiState(
    val peerCount: Int,
    val syncedDocuments: Int,
    val collaborators: List<String>,
    val syncingDocuments: Int,
    val tools: List<ToolItem>,
    val activity: List<ActivityItem>,
)
