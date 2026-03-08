package io.ogwars.cloud.paper.listener

import io.ogwars.cloud.paper.network.NetworkFeatureState
import io.ogwars.cloud.paper.permission.PermissionManager
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ChatListener(
    private val permissionManager: PermissionManager, private val networkFeatureState: NetworkFeatureState
) : Listener {

    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (!networkFeatureState.permissionSystemEnabled) {
            return
        }

        val formatted = buildFormattedMessage(event)
        event.renderer { _, _, _, _ -> deserializeLegacy(formatted) }
    }

    private fun buildFormattedMessage(event: AsyncChatEvent): String {
        val player = event.player
        val cached = permissionManager.getCachedPlayer(player.uniqueId)
        val prefix = cached?.chatPrefix.orEmpty()
        val nameColor = cached?.nameColor ?: DEFAULT_NAME_COLOR
        val suffix = cached?.chatSuffix ?: DEFAULT_CHAT_SUFFIX
        val message = legacySerializer.serialize(event.message())

        return "$prefix$nameColor${player.name}$suffix$message"
    }

    private fun deserializeLegacy(text: String) = legacySerializer.deserialize(text.replace('\u00A7', '&'))

    companion object {
        private const val DEFAULT_NAME_COLOR = "&7"
        private const val DEFAULT_CHAT_SUFFIX = ": &f"
    }
}
