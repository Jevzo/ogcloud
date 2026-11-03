package io.ogwars.cloud.legacy

import io.ogwars.cloud.bukkit.BukkitPlatform
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
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
                    BukkitPlatform.legacy(messages.text(Message.KICK_SERVER_FULL)),
                )
            }

            // The server's own limit comes from server.properties, which OgCloud does not manage.
            event.result == PlayerLoginEvent.Result.KICK_FULL -> {
                event.allow()
            }
        }
    }
}
