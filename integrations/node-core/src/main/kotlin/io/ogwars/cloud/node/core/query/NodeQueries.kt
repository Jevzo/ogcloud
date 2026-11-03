package io.ogwars.cloud.node.core.query

import io.ogwars.cloud.contracts.node.v1.FindPlayerRequest
import io.ogwars.cloud.contracts.node.v1.FindPlayerResponse
import io.ogwars.cloud.contracts.node.v1.GatewayServiceGrpc.GatewayServiceFutureStub
import io.ogwars.cloud.contracts.node.v1.GetPermissionGroupRequest
import io.ogwars.cloud.contracts.node.v1.GetPlayerProfileRequest
import io.ogwars.cloud.contracts.node.v1.GetServerRequest
import io.ogwars.cloud.contracts.node.v1.GroupInfo
import io.ogwars.cloud.contracts.node.v1.ListGroupsRequest
import io.ogwars.cloud.contracts.node.v1.ListNpcsRequest
import io.ogwars.cloud.contracts.node.v1.ListPermissionGroupsRequest
import io.ogwars.cloud.contracts.node.v1.ListPlayersRequest
import io.ogwars.cloud.contracts.node.v1.ListServersRequest
import io.ogwars.cloud.contracts.node.v1.Npc
import io.ogwars.cloud.contracts.node.v1.PermissionGroup
import io.ogwars.cloud.contracts.node.v1.PlayerLocation
import io.ogwars.cloud.contracts.node.v1.PlayerProfile
import io.ogwars.cloud.contracts.node.v1.ServerInfo
import com.google.common.util.concurrent.ListenableFuture
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** What node-core asks the gateway; every answer completes off the server thread, and "not found" is null. */
interface NodeQueries {
    fun server(id: String): CompletableFuture<ServerInfo?>

    /** Every live server, or a group's. */
    fun servers(groupId: String? = null): CompletableFuture<List<ServerInfo>>

    fun findPlayer(uuid: UUID): CompletableFuture<PlayerLocation?>

    fun findPlayer(name: String): CompletableFuture<PlayerLocation?>

    /** The players on a backend or connected through a proxy. */
    fun players(serverId: String): CompletableFuture<List<PlayerLocation>>

    fun profile(player: UUID): CompletableFuture<PlayerProfile?>

    fun permissionGroup(id: String): CompletableFuture<PermissionGroup?>

    fun permissionGroups(): CompletableFuture<List<PermissionGroup>>

    fun groups(): CompletableFuture<List<GroupInfo>>

    fun npcs(groupId: String): CompletableFuture<List<Npc>>
}

/** [NodeQueries] over the gateway's gRPC queries, each with [deadline]. */
class GrpcNodeQueries(
    private val stub: GatewayServiceFutureStub,
    private val deadline: Duration,
    private val executor: Executor,
) : NodeQueries {
    override fun server(id: String) =
        callOrNull { getServer(GetServerRequest.newBuilder().setServerId(id).build()) }.thenApply { it?.server }

    override fun servers(groupId: String?) =
        call {
            listServers(
                ListServersRequest.newBuilder().setGroupId(groupId.orEmpty()).build(),
            )
        }.thenApply { it.serversList }

    override fun findPlayer(uuid: UUID) =
        call {
            findPlayer(
                FindPlayerRequest.newBuilder().setPlayerUuid(uuid.toString()).build(),
            )
        }.thenApply(::locationOf)

    override fun findPlayer(name: String) =
        call { findPlayer(FindPlayerRequest.newBuilder().setPlayerName(name).build()) }.thenApply(::locationOf)

    override fun players(serverId: String) =
        call { listPlayers(ListPlayersRequest.newBuilder().setServerId(serverId).build()) }.thenApply { it.playersList }

    override fun profile(player: UUID) =
        callOrNull {
            getPlayerProfile(GetPlayerProfileRequest.newBuilder().setPlayerUuid(player.toString()).build())
        }.thenApply { it?.profile }

    override fun permissionGroup(id: String) =
        callOrNull {
            getPermissionGroup(GetPermissionGroupRequest.newBuilder().setPermissionGroupId(id).build())
        }.thenApply { it?.permissionGroup }

    override fun permissionGroups() =
        call {
            listPermissionGroups(
                ListPermissionGroupsRequest.getDefaultInstance(),
            )
        }.thenApply { it.permissionGroupsList }

    override fun groups() = call { listGroups(ListGroupsRequest.getDefaultInstance()) }.thenApply { it.groupsList }

    override fun npcs(groupId: String) =
        call { listNpcs(ListNpcsRequest.newBuilder().setGroupId(groupId).build()) }.thenApply { it.npcsList }

    private fun locationOf(response: FindPlayerResponse): PlayerLocation? =
        response.takeIf { it.hasLocation() }?.location

    private fun <T : Any> call(query: GatewayServiceFutureStub.() -> ListenableFuture<T>): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val pending = stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS).query()
        pending.addListener({
            try {
                result.complete(pending.get())
            } catch (e: ExecutionException) {
                result.completeExceptionally(e.cause ?: e)
            }
        }, executor)
        return result
    }

    /** Like [call], but NOT_FOUND completes with null. */
    private fun <T : Any> callOrNull(query: GatewayServiceFutureStub.() -> ListenableFuture<T>): CompletableFuture<T?> =
        call(query).handle { value, error ->
            when {
                error == null -> value
                (error.cause ?: error).isNotFound() -> null
                else -> throw CompletionException(error)
            }
        }

    private fun Throwable.isNotFound() = (this as? StatusRuntimeException)?.status?.code == Status.Code.NOT_FOUND
}
