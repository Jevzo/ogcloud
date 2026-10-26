package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.GameState
import io.ogwars.cloud.contracts.events.v1.GameStateChanged
import io.ogwars.cloud.contracts.events.v1.KillServer
import io.ogwars.cloud.contracts.events.v1.NodeHeartbeat
import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.contracts.events.v1.PlayerDisconnected
import io.ogwars.cloud.contracts.events.v1.PlayerSwitched
import io.ogwars.cloud.contracts.events.v1.PushTemplate
import io.ogwars.cloud.contracts.events.v1.RequestServer
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.contracts.events.v1.StopServer
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.messaging.EventMetadataFactory
import java.time.Clock
import java.util.UUID

/** Builds the events the gateway and the API publish, stamped by [clock]. */
class Events(
    clock: Clock,
) {
    private val metadata = EventMetadataFactory(clock, { UUID.randomUUID() }, "test")
    val admin = Actor(ActorType.USER, "0192d2c1-0000-7000-8000-000000000001")

    fun request(
        serverId: UUID,
        groupId: String,
    ): ServerCommand = command(serverId).setRequest(RequestServer.newBuilder().setGroupId(groupId)).build()

    fun stop(serverId: UUID): ServerCommand =
        command(serverId).setStop(StopServer.newBuilder().setReason("admin")).build()

    fun kill(serverId: UUID): ServerCommand =
        command(serverId).setKill(KillServer.newBuilder().setReason("admin")).build()

    fun push(serverId: UUID): ServerCommand =
        command(serverId).setPushTemplate(PushTemplate.getDefaultInstance()).build()

    fun heartbeat(
        serverId: UUID,
        players: Int,
    ): NodeHeartbeat =
        NodeHeartbeat
            .newBuilder()
            .setMetadata(metadata.create(GATEWAY))
            .setServerId(serverId.toString())
            .setPlayers(players)
            .setMaxPlayers(MAX_PLAYERS)
            .build()

    fun gameState(
        serverId: UUID,
        state: GameState,
    ): GameStateChanged =
        GameStateChanged
            .newBuilder()
            .setMetadata(metadata.create(GATEWAY))
            .setServerId(serverId.toString())
            .setGameState(state)
            .build()

    fun switched(
        player: UUID,
        to: UUID,
    ): PlayerActivity = activity(player).setSwitched(PlayerSwitched.newBuilder().setToServerId(to.toString())).build()

    fun disconnected(
        player: UUID,
        proxy: UUID,
    ): PlayerActivity =
        activity(player).setDisconnected(PlayerDisconnected.newBuilder().setProxyId(proxy.toString())).build()

    private fun command(serverId: UUID) =
        ServerCommand.newBuilder().setMetadata(metadata.create(admin)).setServerId(serverId.toString())

    private fun activity(player: UUID) =
        PlayerActivity.newBuilder().setMetadata(metadata.create(GATEWAY)).setPlayerUuid(player.toString())

    private companion object {
        val GATEWAY = Actor.system("gateway")
        const val MAX_PLAYERS = 50
    }
}
