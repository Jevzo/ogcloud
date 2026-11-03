package io.ogwars.cloud.paper

import io.ogwars.cloud.node.core.format.ChatFormat
import io.ogwars.cloud.node.core.runtime.ServerRuntime
import io.ogwars.cloud.node.core.text.ColorCodes
import io.ogwars.cloud.node.core.text.Message
import com.destroystokyo.paper.event.player.PlayerHandshakeEvent
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.logging.Logger

/** Paper's events, handed to node-core's server runtime. */
internal class PaperListeners(
    private val runtime: ServerRuntime,
    private val tokens: ForwardingTokens,
    private val tablist: TeamTablist,
    private val logger: Logger,
) : Listener {
    private val chat = ChatFormat(runtime.messages)

    /** Only OgCloud's proxies may forward players here (D-0065); first, so no other plugin sees a forged login. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onHandshake(event: PlayerHandshakeEvent) {
        when (val verdict = ForwardingHandshake.verify(event.originalHandshake, tokens.current())) {
            is HandshakeVerdict.Accepted -> {
                val login = verdict.login
                event.setServerHostname(login.serverHostname)
                event.setSocketAddressHostname(login.address)
                event.setUniqueId(login.uuid)
                event.setPropertiesJson(login.propertiesJson)
                event.isCancelled = true
            }

            is HandshakeVerdict.Refused -> {
                logger.warning("Refused a handshake from ${event.originalSocketAddressHostname}: ${verdict.reason}")
                event.isFailed = true
                event.failMessage(legacyText(runtime.messages.text(Message.KICK_LOGIN_FAILED)))
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

    /** The rank's chat head, then the message as typed, so `&` in chat stays literal. */
    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        if (!runtime.network.settings.permissionSystemEnabled) return
        val head = chat.head(runtime.permissions.rank(event.player.uniqueId), event.player.name)
        val color = legacyColor(ColorCodes.lastColor(head))
        event.renderer { _, _, message, _ -> legacyText(head).append(message.colorIfAbsent(color)) }
    }
}
