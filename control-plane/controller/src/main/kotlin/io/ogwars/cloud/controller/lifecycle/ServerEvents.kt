package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.contracts.events.v1.AlertRaised
import io.ogwars.cloud.contracts.events.v1.AlertSeverity
import io.ogwars.cloud.contracts.events.v1.ScalingDecided
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.server.TransferTarget
import io.ogwars.cloud.livestate.toProto
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import org.springframework.stereotype.Component
import java.util.UUID

/** What the lifecycle tells the rest of the platform; Kafka in production, recorded in tests. */
interface ServerEvents {
    fun stateChanged(
        change: ServerChange,
        actor: Actor,
        reason: String,
    )

    fun transferPlayers(
        fromServer: UUID,
        target: TransferTarget,
        reason: String,
    )

    fun crashLoopPaused(
        groupId: GroupId,
        failures: Int,
    )

    fun scalingDecided(entry: ScalingLogEntry)
}

@Component
class KafkaServerEvents(
    private val publisher: EventPublisher,
    private val metadata: EventMetadataFactory,
) : ServerEvents {
    // Notifications only: the live records in Valkey are the truth, which the gateway and the load balancer resync
    // from (T-0404, T-0406), so an event lost after the publisher's retries does not leave them wrong for long.
    override fun stateChanged(
        change: ServerChange,
        actor: Actor,
        reason: String,
    ) {
        val server = change.after
        // jscpd:ignore-start -- same Server fields as livestate's ServerRecord, but a different message and builder
        val event =
            ServerLifecycleChanged
                .newBuilder()
                .setMetadata(metadata.create(actor))
                .setServerId(server.id.toString())
                .setGroupId(server.groupId.value)
                .setGroupType(server.groupType.toProto())
                .setProfileId(server.profileId)
                .setState(server.state.toProto())
                .setPreviousState(change.before.state.toProto())
                .setHost(server.host.orEmpty())
                .setPort(server.port ?: 0)
                .setMaxPlayers(server.maxPlayers)
                .setReason(reason)
                .build()
        // jscpd:ignore-end
        publisher.publish(KafkaTopics.SERVER_LIFECYCLE, server.id.toString(), event)
    }

    override fun transferPlayers(
        fromServer: UUID,
        target: TransferTarget,
        reason: String,
    ) {
        val event =
            TransferRequested
                .newBuilder()
                .setMetadata(metadata.create(SYSTEM))
                .setFromServerId(fromServer.toString())
                .setReason(reason)
                .apply {
                    when (target) {
                        is TransferTarget.ToGroup -> setGroupId(target.groupId.value)
                        TransferTarget.Kick -> setKickMessage(reason)
                    }
                }.build()
        // Waits, so the sweeper only marks a transfer as done once Kafka has it.
        publisher.publishAndWait(KafkaTopics.PLAYER_TRANSFER, fromServer.toString(), event)
    }

    override fun crashLoopPaused(
        groupId: GroupId,
        failures: Int,
    ) {
        val event =
            AlertRaised
                .newBuilder()
                .setMetadata(metadata.create(SYSTEM))
                .setSubjectId(groupId.value)
                .setSeverity(AlertSeverity.ALERT_SEVERITY_CRITICAL)
                .setKind(CRASH_LOOP_PAUSED)
                .putAttributes("failures", failures.toString())
                .build()
        publisher.publish(KafkaTopics.ALERT_RAISED, groupId.value, event)
    }

    override fun scalingDecided(entry: ScalingLogEntry) {
        val event =
            ScalingDecided
                .newBuilder()
                .setMetadata(metadata.create(SYSTEM))
                .setGroupId(entry.groupId.value)
                .setAction(entry.action.toProto())
                .setServerId(entry.serverId?.toString().orEmpty())
                .setReason(entry.reason.name)
                .build()
        publisher.publish(KafkaTopics.SCALING_DECIDED, entry.groupId.value, event)
    }

    private companion object {
        val SYSTEM = Actor.system("controller")
        const val CRASH_LOOP_PAUSED = "group.crash-loop-paused"
    }
}
