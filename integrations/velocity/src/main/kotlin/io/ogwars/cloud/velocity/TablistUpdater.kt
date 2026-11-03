package io.ogwars.cloud.velocity

import io.ogwars.cloud.node.core.format.Tablist
import io.ogwars.cloud.node.core.format.TablistValues
import io.ogwars.cloud.node.core.runtime.ProxyRuntime
import com.velocitypowered.api.proxy.ProxyServer

/** The network's tablist header and footer, refreshed on a timer as in v1, or cleared once when switched off. */
internal class TablistUpdater(
    private val proxy: ProxyServer,
    private val runtime: ProxyRuntime,
    private val proxyName: () -> String,
) {
    @Volatile
    private var shown = false

    fun refresh() {
        val settings = runtime.network.settings
        if (!settings.tablistEnabled) {
            if (shown) proxy.allPlayers.forEach { it.tabList.clearHeaderAndFooter() }
            shown = false
            return
        }
        shown = true
        proxy.allPlayers.forEach { player ->
            val serverId = player.currentServer.map { it.serverInfo.name }.orElse(null)
            val backend = serverId?.let { runtime.directory[it] }
            val values =
                TablistValues(
                    server = backend?.displayName ?: serverId.orEmpty(),
                    group = backend?.groupId.orEmpty(),
                    proxy = proxyName(),
                    onlinePlayers = proxy.playerCount,
                    maxPlayers = settings.maxPlayers,
                    ping = player.ping,
                    permissionGroup =
                        runtime.permissions
                            .rank(player.uniqueId)
                            ?.group
                            ?.name
                            .orEmpty(),
                )
            player.sendPlayerListHeaderAndFooter(
                legacyText(Tablist.fill(settings.tablistHeader, values)),
                legacyText(Tablist.fill(settings.tablistFooter, values)),
            )
        }
    }
}
