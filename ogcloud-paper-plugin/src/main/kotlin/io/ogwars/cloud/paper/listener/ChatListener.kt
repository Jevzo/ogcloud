@file:Suppress("DEPRECATION")

package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.compat.LegacyTextSupport
import io.ogwars.cloud.paper.message.PaperMessages
import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.PermissionManager
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

        event.format = LegacyTextSupport.escapePercentSigns(buildFormattedMessage(event))
    }

    private fun buildFormattedMessage(event: AsyncPlayerChatEvent): String {
        val player = event.player
        val cached = permissionManager.getCachedPlayer(player.uniqueId)

        return PaperMessages.format(
            PaperMessages.Chat.FORMAT,
            "prefix" to LegacyTextSupport.colorize(cached?.chatPrefix.orEmpty()),
            "name_color" to LegacyTextSupport.colorize(cached?.nameColor ?: PaperMessages.Chat.DEFAULT_NAME_COLOR),
            "player_name" to player.name,
            "suffix" to LegacyTextSupport.colorize(cached?.chatSuffix ?: PaperMessages.Chat.DEFAULT_SUFFIX),
            "message" to event.message,
        )
    }
}
