package io.ogwars.cloud.node.core.sdk

import io.ogwars.cloud.contracts.events.v1.PushTemplate
import io.ogwars.cloud.contracts.events.v1.RequestServer
import io.ogwars.cloud.contracts.events.v1.StopServer
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.contracts.node.v1.GameStateUpdate
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.ServerCommandResult
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.node.client.NodeSession
import io.ogwars.cloud.node.core.query.NodeQueries
import io.ogwars.cloud.node.core.runtime.NodeIdentity
import io.ogwars.cloud.node.core.text.Message
import io.ogwars.cloud.node.core.text.Messages
import io.ogwars.cloud.node.core.text.reason
import io.ogwars.cloud.sdk.GameState
import io.ogwars.cloud.sdk.LiveChannels
import io.ogwars.cloud.sdk.OgCloudException
import io.ogwars.cloud.sdk.OgCloudNode
import io.ogwars.cloud.sdk.OgCloudProxy
import io.ogwars.cloud.sdk.OgCloudServer
import io.ogwars.cloud.sdk.PlayerLocation
import io.ogwars.cloud.sdk.RoutingStrategy
import io.ogwars.cloud.sdk.RuntimeNpcs
import io.ogwars.cloud.sdk.ServerInfo
import io.ogwars.cloud.text.toPlain
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import io.ogwars.cloud.contracts.node.v1.PlayerLocation as ProtoPlayerLocation
import io.ogwars.cloud.contracts.node.v1.ServerInfo as ProtoServerInfo

/**
 * The SDK (D-0131) over node-core: lookups ask the gateway, commands go through the session. Plugin callbacks run on
 * [callbacks], never on the session's gRPC thread, so a slow plugin cannot stall the session.
 */
internal open class NodeApi(
    private val identity: NodeIdentity,
    private val queries: NodeQueries,
    protected val session: NodeSession,
    private val messages: Messages,
    override val liveChannels: LiveChannels,
    private val ready: CompletableFuture<Unit>,
    private val callbacks: Executor,
) : OgCloudNode {
    private val log = System.getLogger(NodeApi::class.java.name)

    override val serverId: String get() = identity.serverId
    override val groupId: String get() = identity.groupId
    override val groupType get() = identity.groupType

    override fun servers(): CompletableFuture<List<ServerInfo>> =
        queries.servers().thenApply { it.mapNotNull(ProtoServerInfo::toSdk) }

    override fun servers(groupId: String): CompletableFuture<List<ServerInfo>> =
        queries.servers(groupId).thenApply { it.mapNotNull(ProtoServerInfo::toSdk) }

    override fun server(serverId: String): CompletableFuture<Optional<ServerInfo>> =
        queries.server(serverId).thenApply { Optional.ofNullable(it?.toSdk()) }

    override fun findPlayer(player: UUID): CompletableFuture<Optional<PlayerLocation>> =
        queries.findPlayer(player).thenApply { Optional.ofNullable(it?.let(ProtoPlayerLocation::toSdk)) }

    override fun findPlayer(name: String): CompletableFuture<Optional<PlayerLocation>> =
        queries.findPlayer(name).thenApply { Optional.ofNullable(it?.let(ProtoPlayerLocation::toSdk)) }

    override fun requestServer(groupId: String): CompletableFuture<String> =
        command(ServerCommandRequest.newBuilder().setRequest(RequestServer.newBuilder().setGroupId(groupId)))
            .thenApply { it.serverId }

    override fun transferPlayer(
        player: UUID,
        serverId: String,
    ) = transfer(TransferRequested.newBuilder().setPlayerUuid(player.toString()).setServerId(serverId))

    override fun transferPlayerToGroup(
        player: UUID,
        groupId: String,
        strategy: RoutingStrategy?,
    ) = transfer(
        TransferRequested
            .newBuilder()
            .setPlayerUuid(player.toString())
            .setGroupId(groupId)
            .apply { strategy?.let { setRoutingStrategy(it.toProto()) } },
    )

    override fun onReady(action: Runnable) {
        ready.thenRunAsync(action, callbacks).exceptionally { error ->
            log.log(System.Logger.Level.WARNING, "A plugin's onReady action failed", error)
            null
        }
    }

    /** Sends a server command; a refusal fails the future with an [OgCloudException] in the catalog's plain words. */
    protected fun command(request: ServerCommandRequest.Builder): CompletableFuture<ServerCommandResult> =
        session.serverCommand(request).handleAsync({ result, error ->
            when {
                error != null -> throw OgCloudException(messages.text(Message.ERROR_NOT_CONNECTED).toPlain(), error)
                result.error.isNotEmpty() -> throw OgCloudException(messages.reason(result.error).toPlain())
                else -> result
            }
        }, callbacks)

    private fun transfer(request: TransferRequested.Builder): CompletableFuture<Void> =
        if (session.send(SessionRequest.newBuilder().setTransfer(request).build())) {
            CompletableFuture.completedFuture(null)
        } else {
            CompletableFuture.failedFuture(OgCloudException(messages.text(Message.ERROR_NOT_CONNECTED).toPlain()))
        }
}

/** A Paper or PandaSpigot server's SDK; the platform adapter brings the runtime NPCs. */
internal class ServerApi(
    identity: NodeIdentity,
    queries: NodeQueries,
    session: NodeSession,
    messages: Messages,
    liveChannels: LiveChannels,
    ready: CompletableFuture<Unit>,
    callbacks: Executor,
    override val npcs: RuntimeNpcs,
) : NodeApi(identity, queries, session, messages, liveChannels, ready, callbacks),
    OgCloudServer {
    @Volatile
    private var state = GameState.LOBBY

    override var gameState: GameState
        get() = state
        set(value) {
            state = value
            announceGameState()
        }

    override fun pushTemplate(): CompletableFuture<Void> =
        command(ServerCommandRequest.newBuilder().setPushTemplate(PushTemplate.getDefaultInstance())).thenAccept { }

    override fun stop(): CompletableFuture<Void> =
        command(ServerCommandRequest.newBuilder().setStop(StopServer.newBuilder().setReason(STOP_REASON)))
            .thenAccept { }

    /** Tells the gateway the game state; also after every Welcome, so a change made while reconnecting arrives. */
    fun announceGameState() {
        val update = GameStateUpdate.newBuilder().setGameState(state.toProto())
        session.send(SessionRequest.newBuilder().setGameState(update).build())
    }

    private companion object {
        const val STOP_REASON = "plugin"
    }
}

internal class ProxyApi(
    identity: NodeIdentity,
    queries: NodeQueries,
    session: NodeSession,
    messages: Messages,
    liveChannels: LiveChannels,
    ready: CompletableFuture<Unit>,
    callbacks: Executor,
) : NodeApi(identity, queries, session, messages, liveChannels, ready, callbacks),
    OgCloudProxy
