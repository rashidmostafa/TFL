package app.tfl.navigation

import androidx.annotation.StringRes
import app.tfl.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.feature.chats.navigation.ChatsRoute
import app.tfl.feature.map.navigation.MapRoute
import app.tfl.feature.settings.navigation.SettingsGraph
import app.tfl.feature.settings.navigation.SettingsRoute
import app.tfl.feature.tools.navigation.ToolsRoute
import app.tfl.feature.vault.navigation.VaultRoute
import kotlin.reflect.KClass

/**
 * The five bottom-navigation tabs.
 *
 * @param route what tapping the tab navigates to.
 * @param startRoute the tab's first screen; the bottom bar is shown only on these.
 * @param baseRoute highlights the tab for every screen inside it.
 */
enum class TopLevelDestination(
    val symbol: String,
    @param:StringRes val label: Int,
    val route: Any,
    val startRoute: KClass<*>,
    val baseRoute: KClass<*> = startRoute,
) {
    CHATS(MaterialSymbols.ChatBubble, R.string.nav_chats, ChatsRoute, ChatsRoute::class),
    MAP(MaterialSymbols.Hub, R.string.nav_map, MapRoute, MapRoute::class),
    VAULT(MaterialSymbols.Lock, R.string.nav_vault, VaultRoute, VaultRoute::class),
    TOOLS(MaterialSymbols.Terminal, R.string.nav_tools, ToolsRoute, ToolsRoute::class),
    SETTINGS(MaterialSymbols.Settings, R.string.nav_settings, SettingsGraph, SettingsRoute::class, SettingsGraph::class),
}
