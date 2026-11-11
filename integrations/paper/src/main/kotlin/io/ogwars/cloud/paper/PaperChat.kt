package io.ogwars.cloud.paper

import io.ogwars.cloud.node.core.format.ChatFormat
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.text.endColor
import io.ogwars.cloud.text.toComponent
import io.ogwars.cloud.text.toTextColor
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/** Paper's chat: the rank's head, then the message as typed in the head's end colour, so typed tags stay literal. */
internal class PaperChat(
    private val runtime: ServerRuntime,
) : Listener {
    private val chat = ChatFormat(runtime.messages)

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (!runtime.network.settings.permissionSystemEnabled) return
        val head = chat.head(runtime.permissions.rank(event.player.uniqueId), event.player.name)
        val color = head.endColor()?.toTextColor()
        event.renderer { _, _, message, _ -> head.toComponent().append(message.colorIfAbsent(color)) }
    }
}
