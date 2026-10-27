package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.server.GameState
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.contracts.events.v1.GameState as ProtoGameState
import io.ogwars.cloud.contracts.events.v1.GroupType as ProtoGroupType
import io.ogwars.cloud.contracts.events.v1.JobState as ProtoJobState
import io.ogwars.cloud.contracts.events.v1.RoutingStrategy as ProtoRoutingStrategy
import io.ogwars.cloud.contracts.events.v1.ScalingAction as ProtoScalingAction
import io.ogwars.cloud.contracts.events.v1.ServerState as ProtoServerState

// Domain enums and their Protobuf twins share names after the proto prefix (buf's ENUM_VALUE_PREFIX rule).

fun GroupType.toProto(): ProtoGroupType = ProtoGroupType.valueOf("GROUP_TYPE_$name")

fun ServerState.toProto(): ProtoServerState = ProtoServerState.valueOf("SERVER_STATE_$name")

fun RoutingStrategy.toProto(): ProtoRoutingStrategy = ProtoRoutingStrategy.valueOf("ROUTING_STRATEGY_$name")

fun JobState.toProto(): ProtoJobState = ProtoJobState.valueOf("JOB_STATE_$name")

fun ScalingAction.toProto(): ProtoScalingAction = ProtoScalingAction.valueOf("SCALING_ACTION_$name")

/** Servers without a game state map to UNSPECIFIED. */
fun GameState?.toProto(): ProtoGameState =
    this?.let { ProtoGameState.valueOf("GAME_STATE_$name") } ?: ProtoGameState.GAME_STATE_UNSPECIFIED

fun ProtoGroupType.toDomain(): GroupType = domainEnum(name, "GROUP_TYPE_")

fun ProtoServerState.toDomain(): ServerState = domainEnum(name, "SERVER_STATE_")

fun ProtoJobState.toDomain(): JobState = domainEnum(name, "JOB_STATE_")

fun ProtoScalingAction.toDomain(): ScalingAction = domainEnum(name, "SCALING_ACTION_")

/** UNSPECIFIED means the server has no game state. */
fun ProtoGameState.toDomain(): GameState? =
    if (this ==
        ProtoGameState.GAME_STATE_UNSPECIFIED
    ) {
        null
    } else {
        domainEnum<GameState>(name, "GAME_STATE_")
    }

/** Unknown or unspecified values are errors: they come from a newer or broken writer. */
private inline fun <reified E : Enum<E>> domainEnum(
    protoName: String,
    prefix: String,
): E =
    enumValues<E>().find { prefix + it.name == protoName }
        ?: throw IllegalStateException("No ${E::class.simpleName} for Protobuf value $protoName")
