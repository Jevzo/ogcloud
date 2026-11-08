package io.ogwars.cloud.node.core.sdk

import io.ogwars.cloud.sdk.GameState
import io.ogwars.cloud.sdk.GroupType
import io.ogwars.cloud.sdk.PlayerLocation
import io.ogwars.cloud.sdk.RoutingStrategy
import io.ogwars.cloud.sdk.ServerInfo
import io.ogwars.cloud.sdk.ServerState
import java.util.Optional
import java.util.UUID
import io.ogwars.cloud.contracts.events.v1.GameState as ProtoGameState
import io.ogwars.cloud.contracts.events.v1.GroupType as ProtoGroupType
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy as ProtoRoutingStrategy
import io.ogwars.cloud.contracts.events.v1.ServerState as ProtoServerState
import io.ogwars.cloud.contracts.node.v1.PlayerLocation as ProtoPlayerLocation
import io.ogwars.cloud.contracts.node.v1.ServerInfo as ProtoServerInfo

internal data class ServerView(
    override val serverId: String,
    override val groupId: String,
    override val groupType: GroupType,
    override val profileId: String,
    override val state: ServerState,
    override val gameState: GameState,
    override val host: String,
    override val port: Int,
    override val players: Int,
    override val maxPlayers: Int,
    override val groupMaintenance: Boolean,
) : ServerInfo

internal data class LocationView(
    override val player: UUID,
    override val name: String,
    override val proxyId: String,
    override val serverId: Optional<String>,
) : PlayerLocation

/** Null for a server whose type or state this version does not know, e.g. from a newer gateway; callers skip it. */
internal fun ProtoServerInfo.toSdk(): ServerInfo? {
    val type = groupType.toSdk() ?: return null
    val sdkState = state.toSdk() ?: return null
    return ServerView(
        serverId,
        groupId,
        type,
        profileId,
        sdkState,
        gameState.toSdk(),
        host,
        port,
        players,
        maxPlayers,
        groupMaintenance,
    )
}

internal fun ProtoPlayerLocation.toSdk(): PlayerLocation =
    LocationView(UUID.fromString(playerUuid), playerName, proxyId, Optional.ofNullable(serverId.ifEmpty { null }))

internal fun ProtoGroupType.toSdk(): GroupType? =
    when (this) {
        ProtoGroupType.GROUP_TYPE_DYNAMIC -> GroupType.DYNAMIC
        ProtoGroupType.GROUP_TYPE_STATIC -> GroupType.STATIC
        ProtoGroupType.GROUP_TYPE_PROXY -> GroupType.PROXY
        ProtoGroupType.GROUP_TYPE_UNSPECIFIED, ProtoGroupType.UNRECOGNIZED -> null
    }

internal fun ProtoServerState.toSdk(): ServerState? =
    when (this) {
        ProtoServerState.SERVER_STATE_REQUESTED -> ServerState.REQUESTED
        ProtoServerState.SERVER_STATE_PREPARING -> ServerState.PREPARING
        ProtoServerState.SERVER_STATE_STARTING -> ServerState.STARTING
        ProtoServerState.SERVER_STATE_RUNNING -> ServerState.RUNNING
        ProtoServerState.SERVER_STATE_DRAINING -> ServerState.DRAINING
        ProtoServerState.SERVER_STATE_STOPPING -> ServerState.STOPPING
        ProtoServerState.SERVER_STATE_STOPPED -> ServerState.STOPPED
        ProtoServerState.SERVER_STATE_UNSPECIFIED, ProtoServerState.UNRECOGNIZED -> null
    }

/** Only DYNAMIC servers have a game state; the others count as in the lobby. */
internal fun ProtoGameState.toSdk(): GameState =
    when (this) {
        ProtoGameState.GAME_STATE_INGAME -> {
            GameState.INGAME
        }

        ProtoGameState.GAME_STATE_ENDING -> {
            GameState.ENDING
        }

        ProtoGameState.GAME_STATE_LOBBY, ProtoGameState.GAME_STATE_UNSPECIFIED, ProtoGameState.UNRECOGNIZED -> {
            GameState.LOBBY
        }
    }

internal fun GameState.toProto(): ProtoGameState =
    when (this) {
        GameState.LOBBY -> ProtoGameState.GAME_STATE_LOBBY
        GameState.INGAME -> ProtoGameState.GAME_STATE_INGAME
        GameState.ENDING -> ProtoGameState.GAME_STATE_ENDING
    }

internal fun RoutingStrategy.toProto(): ProtoRoutingStrategy =
    when (this) {
        RoutingStrategy.LEAST_FILLED -> ProtoRoutingStrategy.ROUTING_STRATEGY_LEAST_FILLED
        RoutingStrategy.MOST_FILLED -> ProtoRoutingStrategy.ROUTING_STRATEGY_MOST_FILLED
        RoutingStrategy.BALANCED -> ProtoRoutingStrategy.ROUTING_STRATEGY_BALANCED
        RoutingStrategy.ROUND_ROBIN -> ProtoRoutingStrategy.ROUTING_STRATEGY_ROUND_ROBIN
    }
