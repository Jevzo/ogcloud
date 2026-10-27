package io.ogwars.cloud.controller.readmodel

import io.ogwars.cloud.contracts.NETWORK_SUBJECT
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.NpcUpdated
import io.ogwars.cloud.contracts.events.v1.PermissionGroupUpdated
import io.ogwars.cloud.contracts.events.v1.PlayerPermissionsUpdated
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import org.springframework.stereotype.Component

/** `ogcloud.node.broadcast`: what the gateway fans out to nodes. Keyed by subject, so one subject's updates stay ordered. */
interface NodeBroadcasts {
    fun send(
        subjectId: String,
        update: NodeBroadcast.Builder.() -> Unit,
    )
}

@Component
class KafkaNodeBroadcasts(
    private val publisher: EventPublisher,
    private val metadata: EventMetadataFactory,
) : NodeBroadcasts {
    override fun send(
        subjectId: String,
        update: NodeBroadcast.Builder.() -> Unit,
    ) {
        val broadcast =
            NodeBroadcast
                .newBuilder()
                .setMetadata(metadata.create(SYSTEM))
                .setSubjectId(subjectId)
                .apply(update)
                .build()
        publisher.publish(KafkaTopics.NODE_BROADCAST, subjectId, broadcast)
    }

    private companion object {
        val SYSTEM = Actor.system("controller")
    }
}

fun NodeBroadcasts.network(settings: NetworkSettingsUpdated) = send(NETWORK_SUBJECT) { setNetworkSettings(settings) }

fun NodeBroadcasts.group(group: GroupUpdated) = send(group.groupId) { setGroup(group) }

fun NodeBroadcasts.permissionGroup(
    id: String,
    version: Long,
    deleted: Boolean,
) = send(id) {
    setPermissionGroup(
        PermissionGroupUpdated
            .newBuilder()
            .setPermissionGroupId(id)
            .setVersion(version)
            .setDeleted(deleted),
    )
}

fun NodeBroadcasts.playerPermissions(
    playerUuid: String,
    permissionGroupId: String,
    version: Long,
) = send(playerUuid) {
    setPlayerPermissions(
        PlayerPermissionsUpdated
            .newBuilder()
            .setPlayerUuid(
                playerUuid,
            ).setPermissionGroupId(permissionGroupId)
            .setVersion(version),
    )
}

/** NPC lists are reloaded per group on this signal; NPCs have no row version of their own. */
fun NodeBroadcasts.npc(
    npcId: String,
    groupId: String,
    deleted: Boolean,
) = send(groupId) {
    setNpc(
        NpcUpdated
            .newBuilder()
            .setNpcId(npcId)
            .setGroupId(groupId)
            .setDeleted(deleted),
    )
}
