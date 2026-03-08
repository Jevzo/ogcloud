package io.ogwars.cloud.controller.service

import io.ogwars.cloud.api.event.PermissionExpiryEvent
import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.api.event.PlayerConnectEvent
import io.ogwars.cloud.api.event.PlayerDisconnectEvent
import io.ogwars.cloud.api.event.PlayerSwitchEvent
import io.ogwars.cloud.api.model.PermissionConfig
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.model.PermissionGroupDocument
import io.ogwars.cloud.controller.model.PlayerDocument
import io.ogwars.cloud.controller.redis.PlayerRedisRepository
import io.ogwars.cloud.controller.repository.PermissionGroupRepository
import io.ogwars.cloud.controller.repository.PlayerRepository
import io.ogwars.cloud.controller.repository.WebUserRepository
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PlayerTrackingService(
    private val playerRepository: PlayerRepository,
    private val permissionGroupRepository: PermissionGroupRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val kafkaTemplate: KafkaTemplate<String, PermissionUpdateEvent>,
    private val webUserRepository: WebUserRepository,
    private val networkSettingsService: NetworkSettingsService
) {

    fun handleConnect(event: PlayerConnectEvent) {
        val existing = playerRepository.findById(event.uuid).orElse(null)
        val permissionSystemEnabled = networkSettingsService.findGlobal().general.permissionSystemEnabled
        val defaultGroup = if (permissionSystemEnabled) requireDefaultGroup() else null

        val player = if (existing == null) {
            val newPlayer = PlayerDocument(
                id = event.uuid,
                name = event.name,
                permission = defaultGroup?.let { buildPermanentPermission(it.id) } ?: PermissionConfig(),
                firstJoin = Instant.now()
            )
            playerRepository.save(newPlayer)
            newPlayer
        } else {
            if (existing.name != event.name) {
                val updated = existing.copy(name = event.name)
                playerRepository.save(updated)

                webUserRepository.findByLinkedPlayerUuid(event.uuid).ifPresent { linkedUser ->
                    webUserRepository.save(linkedUser.copy(username = event.name))
                }

                updated
            } else {
                existing
            }
        }

        if (!permissionSystemEnabled || defaultGroup == null) {
            playerRedisRepository.saveSession(event.uuid, player.name, event.proxyId)
            return
        }

        val resolved = resolvePermissionAssignment(player, defaultGroup)
        playerRedisRepository.saveSession(
            event.uuid,
            resolved.player.name,
            event.proxyId,
            resolved.group,
            resolved.player.permission.endMillis
        )
    }

    fun handleDisconnect(event: PlayerDisconnectEvent) {
        playerRedisRepository.deleteSession(event.uuid)
    }

    fun handleSwitch(event: PlayerSwitchEvent) {
        playerRedisRepository.updateServerId(event.uuid, event.serverId)
    }

    fun handlePermissionUpdate(event: PermissionUpdateEvent) {
        if (!networkSettingsService.findGlobal().general.permissionSystemEnabled) {
            return
        }

        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val defaultGroup = requireDefaultGroup()
        val resolved = resolvePermissionAssignment(player, defaultGroup)

        if (playerRedisRepository.isOnline(event.uuid)) {
            playerRedisRepository.updatePermissions(
                event.uuid,
                resolved.group,
                resolved.player.permission.endMillis
            )
        }
    }

    fun handlePermissionExpiry(event: PermissionExpiryEvent) {
        if (!networkSettingsService.findGlobal().general.permissionSystemEnabled) {
            return
        }

        val defaultGroup = requireDefaultGroup()

        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val updated = player.copy(
            permission = buildPermanentPermission(defaultGroup.id)
        )

        playerRepository.save(updated)

        if (playerRedisRepository.isOnline(event.uuid)) {
            playerRedisRepository.updatePermissions(event.uuid, defaultGroup, PERMANENT_PERMISSION_END_MILLIS)
        }

        publishPermissionUpdate(event.uuid, defaultGroup, PERMANENT_PERMISSION_END_MILLIS, PERMISSION_EXPIRY_UPDATED_BY)
    }

    fun handleNetworkFeatureUpdate(permissionSystemEnabled: Boolean) {
        if (!permissionSystemEnabled) {
            return
        }

        val defaultGroup = requireDefaultGroup()
        val onlineUuids = playerRedisRepository.findOnlinePlayerUuids()

        if (onlineUuids.isEmpty()) {
            return
        }

        val playersById = playerRepository.findAllById(onlineUuids).associateBy { it.id }

        onlineUuids.forEach { uuid ->
            val player = playersById[uuid] ?: return@forEach
            val resolved = resolvePermissionAssignment(player, defaultGroup)

            playerRedisRepository.updatePermissions(uuid, resolved.group, resolved.player.permission.endMillis)
            publishPermissionUpdate(
                uuid,
                resolved.group,
                resolved.player.permission.endMillis,
                NETWORK_FEATURE_UPDATED_BY
            )
        }
    }

    private fun requireDefaultGroup() = permissionGroupRepository.findByDefaultTrue()
        ?: throw IllegalStateException(NO_DEFAULT_PERMISSION_GROUP_MESSAGE)

    private fun resolvePermissionAssignment(
        player: PlayerDocument,
        defaultGroup: PermissionGroupDocument
    ): ResolvedPermissionAssignment {
        val assignedGroup = permissionGroupRepository.findById(player.permission.group).orElse(null)
        if (assignedGroup != null) {
            return ResolvedPermissionAssignment(player = player, group = assignedGroup)
        }

        val reassignedPlayer = player.copy(permission = buildPermanentPermission(defaultGroup.id))
        playerRepository.save(reassignedPlayer)

        return ResolvedPermissionAssignment(player = reassignedPlayer, group = defaultGroup)
    }

    private fun publishPermissionUpdate(
        uuid: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
        updatedBy: String
    ) {
        val updateEvent = PermissionUpdateEvent(
            uuid = uuid,
            groupId = group.id,
            groupName = group.name,
            permissions = group.permissions,
            display = group.display,
            weight = group.weight,
            permissionEndMillis = permissionEndMillis,
            updatedBy = updatedBy
        )

        kafkaTemplate.send(KafkaConfig.PERMISSION_UPDATE, uuid, updateEvent)
    }

    private fun buildPermanentPermission(groupId: String): PermissionConfig {
        return PermissionConfig(group = groupId)
    }

    private data class ResolvedPermissionAssignment(
        val player: PlayerDocument,
        val group: PermissionGroupDocument
    )

    companion object {
        private const val NO_DEFAULT_PERMISSION_GROUP_MESSAGE = "No default permission group configured"
        private const val PERMANENT_PERMISSION_END_MILLIS = -1L
        private const val PERMISSION_EXPIRY_UPDATED_BY = "expiry"
        private const val NETWORK_FEATURE_UPDATED_BY = "network-feature"
    }
}
