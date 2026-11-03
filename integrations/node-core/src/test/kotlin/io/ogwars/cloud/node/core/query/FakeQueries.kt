package io.ogwars.cloud.node.core.query

import io.ogwars.cloud.contracts.node.v1.GroupInfo
import io.ogwars.cloud.contracts.node.v1.Npc
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerLocation
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.ResolveLoginRequest
import io.ogwars.cloud.contracts.node.v1.ResolveLoginResponse
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

/** Answers queries from maps the test fills. */
class FakeQueries : NodeQueries {
    val servers = mutableListOf<ServerInfo>()
    val players = mutableListOf<PlayerLocation>()
    val profiles = mutableMapOf<UUID, PlayerProfile>()
    val permissionGroups = mutableMapOf<String, PermissionGroup>()
    val groups = mutableListOf<GroupInfo>()
    val npcs = mutableListOf<Npc>()

    /** The answer to logins; null fails them like a gateway outage. */
    var login: ResolveLoginResponse? = null

    override fun server(id: String): CompletableFuture<ServerInfo?> =
        completedFuture(
            servers.firstOrNull {
                it.serverId ==
                    id
            },
        )

    override fun servers(groupId: String?): CompletableFuture<List<ServerInfo>> =
        completedFuture(servers.filter { groupId == null || it.groupId == groupId })

    override fun findPlayer(uuid: UUID): CompletableFuture<PlayerLocation?> =
        completedFuture(players.firstOrNull { it.playerUuid == uuid.toString() })

    override fun findPlayer(name: String): CompletableFuture<PlayerLocation?> =
        completedFuture(players.firstOrNull { it.playerName.equals(name, ignoreCase = true) })

    override fun players(serverId: String): CompletableFuture<List<PlayerLocation>> =
        completedFuture(players.filter { it.serverId == serverId || it.proxyId == serverId })

    override fun profile(player: UUID): CompletableFuture<PlayerProfile?> = completedFuture(profiles[player])

    override fun permissionGroup(id: String): CompletableFuture<PermissionGroup?> =
        completedFuture(permissionGroups[id])

    override fun permissionGroups(): CompletableFuture<List<PermissionGroup>> =
        completedFuture(permissionGroups.values.toList())

    override fun groups(): CompletableFuture<List<GroupInfo>> = completedFuture(groups.toList())

    override fun npcs(groupId: String): CompletableFuture<List<Npc>> =
        completedFuture(
            npcs.filter {
                it.groupId ==
                    groupId
            },
        )

    override fun resolveLogin(request: ResolveLoginRequest): CompletableFuture<ResolveLoginResponse> =
        login?.let { completedFuture(it) } ?: CompletableFuture.failedFuture(IllegalStateException("gateway down"))
}
