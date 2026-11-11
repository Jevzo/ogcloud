package io.ogwars.cloud.bukkit

import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.text.toLegacy
import com.destroystokyo.paper.event.player.PlayerHandshakeEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.logging.Logger

/** The events every backend handles the same way: forwarded logins, joins and quits. */
class BackendListeners(
    private val runtime: ServerRuntime,
    private val tokens: ForwardingTokens,
    private val tablist: TeamTablist,
    private val logger: Logger,
) : Listener {
    /** Only OgCloud's proxies may forward players here (D-0065); first, so no other plugin sees a forged login. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onHandshake(event: PlayerHandshakeEvent) {
        // Paper fires this cancelled while settings.bungeecord is on (D-0119) and then parses the raw handshake itself,
        // which accepts any login and keeps the token in the profile; uncancelled, it takes our outcome.
        event.isCancelled = false
        val verdict =
            try {
                ForwardingHandshake.verify(event.originalHandshake, tokens.current())
            } catch (e: Exception) {
                // An unreadable token file or handshake must not let a login through unchecked.
                HandshakeVerdict.Refused("could not check the handshake: $e")
            }
        when (verdict) {
            is HandshakeVerdict.Accepted -> {
                val login = verdict.login
                event.serverHostname = login.serverHostname
                event.socketAddressHostname = login.address
                event.uniqueId = login.uuid
                event.propertiesJson = login.propertiesJson
            }

            is HandshakeVerdict.Refused -> {
                logger.warning("Refused a handshake from ${event.originalSocketAddressHostname}: ${verdict.reason}")
                event.isFailed = true
                // The 1.8 API the shared code targets takes only a string, so Paper gets nearest-colour legacy text too.
                event.failMessage = runtime.messages.text(Message.KICK_LOGIN_FAILED).toLegacy()
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        PermissionInjector.inject(player) { p, node ->
            runtime.permissions.decide(p.uniqueId, node, runtime.network.settings.permissionSystemEnabled)
        }
        runtime.joined(player.uniqueId)
        tablist.show(player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        runtime.left(event.player.uniqueId)
        tablist.remove(event.player)
    }
}
