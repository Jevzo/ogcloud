package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.contracts.events.v1.ConfigChanged.ChangeCase
import io.ogwars.cloud.contracts.events.v1.EntityChanged
import io.ogwars.cloud.contracts.events.v1.GroupUpdated
import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.controller.readmodel.NETWORK_SUBJECT
import io.ogwars.cloud.controller.readmodel.NodeBroadcasts
import io.ogwars.cloud.controller.readmodel.PlayerPermissions
import io.ogwars.cloud.controller.readmodel.ReadModelMessages
import io.ogwars.cloud.controller.readmodel.group
import io.ogwars.cloud.controller.readmodel.network
import io.ogwars.cloud.controller.readmodel.npc
import io.ogwars.cloud.controller.readmodel.permissionGroup
import io.ogwars.cloud.controller.template.TemplateVersions
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.NpcStore
import io.ogwars.cloud.domain.store.PermissionGroupStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.messaging.NonRetryableEventException
import org.springframework.stereotype.Component

/**
 * `ogcloud.config.changed`: reloads the entity from PostgreSQL, refreshes its read model and tells the nodes.
 * Each subject has one key, so one consumer handles its changes in order and no lock is needed.
 */
@Component
class ConfigChangedHandler(
    private val groups: GroupStore,
    private val network: NetworkSettingsStore,
    private val permissionGroups: PermissionGroupStore,
    private val npcs: NpcStore,
    private val readModels: ReadModelStore,
    private val broadcasts: NodeBroadcasts,
    private val permissions: PlayerPermissions,
    private val sessions: SessionStore,
    private val templates: TemplateVersions,
) {
    fun handle(event: ConfigChanged) {
        when (event.changeCase) {
            ChangeCase.GROUP -> group(event.group)

            ChangeCase.NETWORK_SETTINGS -> networkSettings()

            ChangeCase.PERMISSION_GROUP -> permissionGroup(event.permissionGroup.id)

            ChangeCase.DEFAULT_PERMISSION_GROUP -> permissionGroup(event.defaultPermissionGroup.permissionGroupId)

            ChangeCase.PLAYER_RANK -> permissions.publish(event.playerRank.playerUuid.toUuid("player_uuid"))

            ChangeCase.NPC -> npc(event.npc, event.subjectId)

            ChangeCase.CHANGE_NOT_SET, null -> throw NonRetryableEventException(
                "Config change ${event.metadata.eventId} carries no change",
            )
        }
    }

    private fun group(change: EntityChanged) {
        val id = change.id.toGroupId()
        val group = groups.find(id)
        if (change.deleted || group == null) {
            readModels.delete(ReadModelKind.GROUP, id.value)
            readModels.delete(ReadModelKind.ACTIVE_TEMPLATE, id.value)
            broadcasts.group(
                GroupUpdated
                    .newBuilder()
                    .setGroupId(id.value)
                    .setDeleted(true)
                    .build(),
            )
            return
        }
        val message = ReadModelMessages.group(group)
        readModels.put(ReadModelKind.GROUP, id.value, message.toByteArray())
        // A promote or rollback by the API arrives as a group change.
        templates.publishActive(group.value)
        broadcasts.group(message)
    }

    private fun networkSettings() {
        val message = ReadModelMessages.networkSettings(network.load())
        val previous =
            readModels
                .get(
                    ReadModelKind.NETWORK_SETTINGS,
                    NETWORK_SUBJECT,
                )?.let(NetworkSettingsUpdated::parseFrom)
        readModels.put(ReadModelKind.NETWORK_SETTINGS, NETWORK_SUBJECT, message.toByteArray())
        broadcasts.network(message)
        if (previous?.permissionSystemEnabled == false && message.permissionSystemEnabled) resyncOnlinePlayers()
    }

    /** The permission system was switched back on: every online player's proxy needs their rank again. */
    private fun resyncOnlinePlayers() {
        sessions.onlinePlayers().forEach(permissions::publish)
    }

    private fun permissionGroup(id: String) {
        val group = permissionGroups.find(id)
        if (group == null) {
            readModels.delete(ReadModelKind.PERMISSION_GROUP, id)
            broadcasts.permissionGroup(id, version = 0, deleted = true)
            return
        }
        readModels.put(ReadModelKind.PERMISSION_GROUP, id, ReadModelMessages.permissionGroup(group).toByteArray())
        broadcasts.permissionGroup(id, group.revision, deleted = false)
    }

    private fun npc(
        change: EntityChanged,
        subjectId: String,
    ) {
        val groupId = subjectId.toGroupId()
        readModels.put(
            ReadModelKind.NPCS_BY_GROUP,
            groupId.value,
            ReadModelMessages.npcs(npcs.findByGroup(groupId)).toByteArray(),
        )
        broadcasts.npc(change.id, groupId.value, change.deleted)
    }

    private fun String.toGroupId(): GroupId =
        try {
            GroupId(this)
        } catch (e: IllegalArgumentException) {
            throw NonRetryableEventException("Invalid group id '$this'", e)
        }
}
