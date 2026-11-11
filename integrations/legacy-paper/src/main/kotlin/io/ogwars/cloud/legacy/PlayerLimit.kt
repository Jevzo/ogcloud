package io.ogwars.cloud.legacy

import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.text.toLegacy
import org.bukkit.Server
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerLoginEvent

/** The group's players per server (D-0134): 1.8 cannot change its max players, so logins are checked here. */
internal class PlayerLimit(
    private val server: Server,
    private val maxPlayers: Int,
    private val messages: Messages,
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onLogin(event: PlayerLoginEvent) {
        when {
            server.onlinePlayers.size >= maxPlayers -> {
                event.disallow(
                    PlayerLoginEvent.Result.KICK_FULL,
                    messages.text(Message.KICK_SERVER_FULL).toLegacy(),
                )
            }

            // The server's own max-players is not the group's limit, so its full verdict is lifted.
            event.result == PlayerLoginEvent.Result.KICK_FULL -> {
                event.allow()
            }
        }
    }
}
