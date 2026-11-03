package io.ogwars.cloud.paper

import io.ogwars.cloud.node.core.format.ChatFormat
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.ColorCodes
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/** Paper's chat: the rank's head, then the message as typed, so `&` in chat stays literal. */
internal class PaperChat(
    private val runtime: ServerRuntime,
) : Listener {
    private val chat = ChatFormat(runtime.messages)

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (!runtime.network.settings.permissionSystemEnabled) return
        val head = chat.head(runtime.permissions.rank(event.player.uniqueId), event.player.name)
        val color = legacyColor(ColorCodes.lastColor(head))
        event.renderer { _, _, message, _ -> legacyText(head).append(message.colorIfAbsent(color)) }
    }
}
