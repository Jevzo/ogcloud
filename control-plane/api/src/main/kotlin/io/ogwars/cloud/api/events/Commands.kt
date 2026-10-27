package io.ogwars.cloud.api.events

import io.ogwars.cloud.contracts.events.v1.KillServer
import io.ogwars.cloud.contracts.events.v1.LinkCodeIssued
import io.ogwars.cloud.contracts.events.v1.NodeCommand
import io.ogwars.cloud.contracts.events.v1.PushTemplate
import io.ogwars.cloud.contracts.events.v1.RequestServer
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.contracts.events.v1.StopServer
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.network.RoutingStrategy
import io.ogwars.cloud.livestate.toProto
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import org.springframework.stereotype.Component
import java.util.UUID

/** Where a transfer sends players: a server, any server of a group, or out of the network. */
sealed interface TransferDestination {
    data class Server(
        val id: UUID,
    ) : TransferDestination

    data class Group(
        val id: GroupId,
        val routing: RoutingStrategy?,
    ) : TransferDestination

    data class Kick(
        val message: String,
    ) : TransferDestination
}

/**
 * Commands for the controller and the nodes. Unlike [Announcements], nothing is saved first, so a failed publish
 * fails the request with [io.ogwars.cloud.messaging.PublishException] (D-0101).
 */
@Component
class Commands(
    private val publisher: EventPublisher,
    private val metadata: EventMetadataFactory,
) {
    fun requestServer(
        actor: Actor,
        serverId: UUID,
        groupId: GroupId,
    ) = server(actor, serverId) { setRequest(RequestServer.newBuilder().setGroupId(groupId.value)) }

    fun stopServer(
        actor: Actor,
        serverId: UUID,
        reason: String,
    ) = server(actor, serverId) { setStop(StopServer.newBuilder().setReason(reason)) }

    fun killServer(
        actor: Actor,
        serverId: UUID,
        reason: String,
    ) = server(actor, serverId) { setKill(KillServer.newBuilder().setReason(reason)) }

    fun pushTemplate(
        actor: Actor,
        serverId: UUID,
    ) = server(actor, serverId) { setPushTemplate(PushTemplate.getDefaultInstance()) }

    fun transferPlayer(
        actor: Actor,
        playerUuid: UUID,
        destination: TransferDestination,
    ) = transfer(actor, playerUuid.toString()) { setPlayerUuid(playerUuid.toString()).to(destination) }

    fun transferAll(
        actor: Actor,
        fromServerId: UUID,
        destination: TransferDestination,
    ) = transfer(actor, fromServerId.toString()) { setFromServerId(fromServerId.toString()).to(destination) }

    /** Runs [command] as console on every node, one server, or every server of a group. */
    fun console(
        actor: Actor,
        target: NodeCommand.Builder.() -> Unit,
        key: String,
        command: String,
    ) = publisher.publishAndWait(
        KafkaTopics.NODE_COMMAND,
        key,
        NodeCommand
            .newBuilder()
            .setMetadata(metadata.create(actor))
            .apply(target)
            .setCommand(command)
            .build(),
    )

    /** Shows a web-account link code to the player in game (D-0106). */
    fun linkCode(
        actor: Actor,
        playerUuid: UUID,
        code: String,
    ) = publisher.publishAndWait(
        KafkaTopics.ACCOUNT_LINK,
        playerUuid.toString(),
        LinkCodeIssued
            .newBuilder()
            .setMetadata(metadata.create(actor))
            .setPlayerUuid(playerUuid.toString())
            .setCode(code)
            .build(),
    )

    private fun server(
        actor: Actor,
        serverId: UUID,
        command: ServerCommand.Builder.() -> Unit,
    ) = publisher.publishAndWait(
        KafkaTopics.SERVER_COMMAND,
        serverId.toString(),
        ServerCommand
            .newBuilder()
            .setMetadata(metadata.create(actor))
            .setServerId(serverId.toString())
            .apply(command)
            .build(),
    )

    private fun transfer(
        actor: Actor,
        key: String,
        transfer: TransferRequested.Builder.() -> Unit,
    ) = publisher.publishAndWait(
        KafkaTopics.PLAYER_TRANSFER,
        key,
        TransferRequested
            .newBuilder()
            .setMetadata(metadata.create(actor))
            .apply(transfer)
            .build(),
    )

    private fun TransferRequested.Builder.to(destination: TransferDestination): TransferRequested.Builder =
        when (destination) {
            is TransferDestination.Server -> {
                setServerId(destination.id.toString())
            }

            is TransferDestination.Group -> {
                setGroupId(destination.id.value).apply {
                    destination.routing?.let { setRoutingStrategy(it.toProto()) }
                }
            }

            is TransferDestination.Kick -> {
                setKickMessage(destination.message)
            }
        }
}
