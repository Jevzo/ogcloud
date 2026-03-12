package io.ogwars.cloud.velocity.server

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerInfo
import org.slf4j.Logger
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

class ServerRegistry(
    private val proxy: ProxyServer,
    private val logger: Logger,
) {
    private val registeredServers = ConcurrentHashMap<String, RegisteredServer>()
    private val serverIdsByRegisteredName = ConcurrentHashMap<String, String>()
    private val serverGroups = ConcurrentHashMap<String, String>()
    private val displayNames = ConcurrentHashMap<String, String>()
    private val drainingServers: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val maintenanceGroups: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun registerServer(
        serverId: String,
        group: String,
        address: InetSocketAddress,
        displayName: String,
    ) {
        registeredServers[serverId]?.let(::unregisterRegisteredServer)

        val serverName = "$group-$serverId"
        val serverInfo = ServerInfo(serverName, address)
        val registered = proxy.registerServer(serverInfo)

        registeredServers[serverId] = registered
        serverIdsByRegisteredName[serverInfo.name] = serverId
        serverGroups[serverId] = group
        displayNames[serverId] = displayName

        logger.info("Registered server: {} at {}", displayName, address)
    }

    fun unregisterServer(serverId: String) {
        drainingServers.remove(serverId)
        serverGroups.remove(serverId)

        val removedDisplayName = displayNames.remove(serverId)

        registeredServers.remove(serverId)?.let { registered ->
            unregisterRegisteredServer(registered)

            logger.info("Unregistered server: {}", removedDisplayName ?: registered.serverInfo.name)
        }
    }

    fun markDraining(serverId: String) {
        drainingServers.add(serverId)
        logger.info("Server marked as draining: {}", serverId)
    }

    fun setGroupMaintenance(
        group: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            maintenanceGroups.add(group)

            logger.info("Group maintenance enabled: {}", group)
        } else {
            maintenanceGroups.remove(group)

            logger.info("Group maintenance disabled: {}", group)
        }
    }

    fun isGroupInMaintenance(group: String): Boolean = maintenanceGroups.contains(group)

    fun getServer(serverId: String): RegisteredServer? = registeredServers[serverId]

    fun findServerIdByRegistered(registered: RegisteredServer): String? =
        serverIdsByRegisteredName[registered.serverInfo.name]

    fun getServersByGroup(
        group: String,
        includeMaintenance: Boolean = false,
    ): List<RegisteredServer> {
        if (!includeMaintenance && maintenanceGroups.contains(group)) return emptyList()
        return serverGroups.entries
            .filter { it.value == group && !drainingServers.contains(it.key) }
            .mapNotNull { registeredServers[it.key] }
    }

    fun getPlayersOnServer(serverId: String): Collection<Player> =
        registeredServers[serverId]?.playersConnected ?: emptyList()

    fun getPlayersInGroup(group: String): Collection<Player> =
        serverGroups.entries
            .filter { it.value == group }
            .mapNotNull { registeredServers[it.key] }
            .flatMap { it.playersConnected }

    fun getDisplayName(serverId: String): String? = displayNames[serverId]

    fun getAllDisplayNames(): Map<String, String> = displayNames.toMap()

    fun findServerIdByDisplayName(displayName: String): String? =
        displayNames.entries
            .firstOrNull {
                it.value == displayName
            }?.key

    fun getGroupForServer(serverId: String): String? = serverGroups[serverId]

    private fun unregisterRegisteredServer(registered: RegisteredServer) {
        serverIdsByRegisteredName.remove(registered.serverInfo.name)
        proxy.unregisterServer(registered.serverInfo)
    }
}
