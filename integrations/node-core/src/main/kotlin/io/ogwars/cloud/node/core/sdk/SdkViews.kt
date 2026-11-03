package io.ogwars.cloud.node.core.sdk

import io.ogwars.cloud.sdk.GameState
import io.ogwars.cloud.sdk.GroupType
import io.ogwars.cloud.sdk.PlayerLocation
import io.ogwars.cloud.sdk.RoutingStrategy
import io.ogwars.cloud.sdk.ServerInfo
import io.ogwars.cloud.sdk.ServerState
import java.util.UUID
import io.ogwars.cloud.contracts.events.v1.GameState as ProtoGameState
import io.ogwars.cloud.contracts.events.v1.GroupType as ProtoGroupType
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy as ProtoRoutingStrategy
import io.ogwars.cloud.contracts.events.v1.ServerState as ProtoServerState
import io.ogwars.cloud.contracts.node.v1.PlayerLocation as ProtoPlayerLocation
import io.ogwars.cloud.contracts.node.v1.ServerInfo as ProtoServerInfo

// The SDK's enums share names with their Protobuf twins after the prefix (buf's ENUM_VALUE_PREFIX rule).

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
    override val serverId: String,
) : PlayerLocation

internal fun ProtoServerInfo.toSdk(): ServerInfo =
    ServerView(
        serverId,
        groupId,
        groupType.toSdk(),
        profileId,
        ServerState.valueOf(state.name.removePrefix("SERVER_STATE_")),
        gameState.toSdk(),
        host,
        port,
        players,
        maxPlayers,
        groupMaintenance,
    )

internal fun ProtoPlayerLocation.toSdk(): PlayerLocation =
    LocationView(UUID.fromString(playerUuid), playerName, proxyId, serverId)

internal fun ProtoGroupType.toSdk(): GroupType = GroupType.valueOf(name.removePrefix("GROUP_TYPE_"))

/** Only DYNAMIC servers have a game state; the others count as in the lobby. */
internal fun ProtoGameState.toSdk(): GameState =
    if (this ==
        ProtoGameState.GAME_STATE_UNSPECIFIED
    ) {
        GameState.LOBBY
    } else {
        GameState.valueOf(name.removePrefix("GAME_STATE_"))
    }

internal fun GameState.toProto(): ProtoGameState = ProtoGameState.valueOf("GAME_STATE_$name")

internal fun RoutingStrategy.toProto(): ProtoRoutingStrategy = ProtoRoutingStrategy.valueOf("ROUTING_STRATEGY_$name")
