package io.ogwars.cloud.legacy

import io.ogwars.cloud.node.core.format.ChatFormat
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.text.RichText
import io.ogwars.cloud.text.endColor
import io.ogwars.cloud.text.legacyCode
import io.ogwars.cloud.text.toLegacy
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/** 1.8 chat: the rank's head in the format, the typed message as `%2$s`, so tags typed in chat stay literal. */
internal class LegacyChat(
    private val runtime: ServerRuntime,
) : Listener {
    private val chat = ChatFormat(runtime.messages)

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (!runtime.network.settings.permissionSystemEnabled) return
        event.format = format(chat.head(runtime.permissions.rank(event.player.uniqueId), event.player.name))
    }

    internal companion object {
        const val MESSAGE = "%2\$s"

        /** As Paper's chat: the message takes the head's end colour, but none of its formats. */
        fun format(head: RichText): String {
            val color = head.endColor()?.let { "§" + legacyCode(it) }.orEmpty()
            // The format is a printf pattern, so a `%` in a prefix must not read as a placeholder.
            return head.toLegacy().replace("%", "%%") + "§r" + color + MESSAGE
        }
    }
}
