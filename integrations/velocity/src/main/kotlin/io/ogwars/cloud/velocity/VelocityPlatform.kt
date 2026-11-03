package io.ogwars.cloud.velocity

import io.ogwars.cloud.node.core.runtime.HeartbeatSample
import io.ogwars.cloud.node.core.runtime.OnlinePlayer
import io.ogwars.cloud.node.core.runtime.ProxyPlatform
import io.ogwars.cloud.node.core.state.Backend
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.ServerInfo
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * node-core's view of Velocity. Backends register under their server id, so a player's current server name is the
 * id itself and group ids may contain dashes (v1 bug 2).
 */
internal class VelocityPlatform(
    private val proxy: ProxyServer,
    private val via: ViaProtocols?,
) : ProxyPlatform {
    override fun onlinePlayers(): List<OnlinePlayer> =
        proxy.allPlayers.map { player ->
            OnlinePlayer(player.uniqueId, player.username, player.currentServer.map { it.serverInfo.name }.orElse(null))
        }

    override fun sample(): HeartbeatSample {
        val memory = Runtime.getRuntime()
        // Velocity has no tick loop, so there is no tick rate to report.
        return HeartbeatSample(
            proxy.playerCount,
            0.0,
            0.0,
            memory.totalMemory() - memory.freeMemory(),
            memory.maxMemory(),
        )
    }

    override fun hasPermission(
        player: UUID,
        node: String,
    ): Boolean = proxy.getPlayer(player).map { it.hasPermission(node) }.orElse(false)

    override fun message(
        player: UUID,
        text: String,
    ): Boolean {
        val online = proxy.getPlayer(player).orElse(null) ?: return false
        online.sendMessage(Texts.of(text))
        return true
    }

    override fun runConsoleCommand(command: String): CompletableFuture<Boolean> =
        proxy.commandManager.executeAsync(proxy.consoleCommandSource, command)

    override fun registered(backend: Backend) {
        // A new address replaces the old registration; players on it keep their connection.
        proxy.getServer(backend.id).ifPresent { proxy.unregisterServer(it.serverInfo) }
        proxy.registerServer(ServerInfo(backend.id, InetSocketAddress(backend.host, backend.port)))
        via?.register(backend.id, backend.protocol)
    }

    override fun unregistered(backend: Backend) {
        proxy.getServer(backend.id).ifPresent { proxy.unregisterServer(it.serverInfo) }
        via?.unregister(backend.id)
    }

    override fun connect(
        player: UUID,
        backend: Backend,
    ) {
        val online = proxy.getPlayer(player).orElse(null) ?: return
        proxy.getServer(backend.id).ifPresent { online.createConnectionRequest(it).fireAndForget() }
    }

    override fun kick(
        player: UUID,
        text: String,
    ) {
        proxy.getPlayer(player).ifPresent { it.disconnect(Texts.of(text)) }
    }
}
