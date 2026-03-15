package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.message.PaperMessages
import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.PermissionManager
import org.bukkit.ChatColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

class ChatListener(
    private val permissionManager: PermissionManager,
    private val networkFeatureState: NetworkFeatureState,
) : Listener {
    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!networkFeatureState.permissionSystemEnabled) {
            return
        }

        event.format = escapePercents(buildFormattedMessage(event))
    }

    private fun buildFormattedMessage(event: AsyncPlayerChatEvent): String {
        val player = event.player
        val cached = permissionManager.getCachedPlayer(player.uniqueId)

        return translateLegacy(
            PaperMessages.format(
                PaperMessages.Chat.FORMAT,
                "prefix" to cached?.chatPrefix.orEmpty(),
                "name_color" to (cached?.nameColor ?: PaperMessages.Chat.DEFAULT_NAME_COLOR),
                "player_name" to player.name,
                "suffix" to (cached?.chatSuffix ?: PaperMessages.Chat.DEFAULT_SUFFIX),
                "message" to event.message,
            ),
        )
    }

    private fun translateLegacy(text: String): String =
        ChatColor.translateAlternateColorCodes('&', text.replace('\u00A7', '&'))

    private fun escapePercents(text: String): String = text.replace("%", "%%")
}
