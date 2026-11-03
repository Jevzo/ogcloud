package io.ogwars.cloud.api.ingame

import io.ogwars.cloud.api.audit.AuditTrail
import io.ogwars.cloud.api.groups.GROUPS_PATH
import io.ogwars.cloud.api.groups.GroupManagement
import io.ogwars.cloud.api.network.NETWORK_PATH
import io.ogwars.cloud.api.network.NetworkManagement
import io.ogwars.cloud.api.permissions.PermissionGroups
import io.ogwars.cloud.api.permissions.RANK_PATH
import io.ogwars.cloud.contracts.internal.v1.AdminChangeRequested
import io.ogwars.cloud.contracts.internal.v1.AdminChangeRequested.ChangeCase
import io.ogwars.cloud.contracts.toJavaDuration
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.ListenerContainers
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.ogwars.cloud.messaging.actor
import io.ogwars.cloud.messaging.protobufContainer
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * `ogcloud.admin.change`: maintenance and rank changes of in-game admins, applied like dashboard edits and audited
 * as the player with the dashboard's action names (D-0132).
 */
@Component
class InGameAdminChanges(
    components: KafkaComponents,
    template: KafkaTemplate<String, ByteArray>,
    private val groups: GroupManagement,
    private val network: NetworkManagement,
    private val ranks: PermissionGroups,
    private val trail: AuditTrail,
) : ListenerContainers() {
    override val containers =
        listOf(
            components
                .listenerContainerFactory(GROUP, template)
                .protobufContainer(KafkaTopics.ADMIN_CHANGE, AdminChangeRequested.parser(), ::handle),
        )

    fun handle(change: AdminChangeRequested) {
        val actor = change.metadata.actor()
        applyOrRefuse(change.metadata.eventId) {
            when (change.changeCase) {
                ChangeCase.GROUP_MAINTENANCE -> {
                    val id = GroupId(change.groupMaintenance.groupId)
                    groups.setMaintenance(actor, id, change.groupMaintenance.enabled)
                    trail.record(actor, "PUT $GROUPS_PATH/{id}", "groups", id.value)
                }

                ChangeCase.NETWORK_MAINTENANCE -> {
                    network.setMaintenance(actor, change.networkMaintenance.enabled)
                    trail.record(actor, "PUT $NETWORK_PATH", "network", "")
                }

                ChangeCase.RANK -> {
                    val rank = change.rank
                    val player = UUID.fromString(rank.playerUuid)
                    val duration = if (rank.hasDuration()) rank.duration.toJavaDuration() else null
                    ranks.setRank(actor, player, rank.permissionGroupId, duration)
                    trail.record(actor, "PUT $RANK_PATH", "players", player.toString())
                }

                ChangeCase.CHANGE_NOT_SET, null -> {
                    throw NonRetryableEventException("Admin change ${change.metadata.eventId} carries no change")
                }
            }
        }
    }

    private companion object {
        const val GROUP = "ogcloud-api-admin-changes"
    }
}
