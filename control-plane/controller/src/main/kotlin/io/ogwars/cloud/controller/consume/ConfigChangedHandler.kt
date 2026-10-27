package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.contracts.events.v1.ConfigChanged.ChangeCase
import io.ogwars.cloud.controller.readmodel.PlayerPermissions
import io.ogwars.cloud.controller.readmodel.ReadModelSync
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.ogwars.cloud.messaging.toUuid
import org.springframework.stereotype.Component

/**
 * `ogcloud.config.changed`: reloads the entity from PostgreSQL, refreshes its read model and tells the nodes.
 * Each subject has one key, so one consumer handles its changes in order and no lock is needed.
 */
@Component
class ConfigChangedHandler(
    private val sync: ReadModelSync,
    private val permissions: PlayerPermissions,
) {
    fun handle(event: ConfigChanged) {
        when (event.changeCase) {
            ChangeCase.GROUP -> sync.group(event.group.id.toGroupId(), event.group.deleted)

            ChangeCase.NETWORK_SETTINGS -> sync.networkSettings()

            ChangeCase.PERMISSION_GROUP -> sync.permissionGroup(event.permissionGroup.id)

            ChangeCase.DEFAULT_PERMISSION_GROUP -> sync.permissionGroup(event.defaultPermissionGroup.permissionGroupId)

            ChangeCase.PLAYER_RANK -> permissions.publish(event.playerRank.playerUuid.toUuid("player_uuid"))

            ChangeCase.NPC -> sync.npc(event.npc.id, event.subjectId.toGroupId(), event.npc.deleted)

            ChangeCase.CHANGE_NOT_SET, null -> throw NonRetryableEventException(
                "Config change ${event.metadata.eventId} carries no change",
            )
        }
    }

    private fun String.toGroupId(): GroupId =
        try {
            GroupId(this)
        } catch (e: IllegalArgumentException) {
            throw NonRetryableEventException("Invalid group id '$this'", e)
        }
}
