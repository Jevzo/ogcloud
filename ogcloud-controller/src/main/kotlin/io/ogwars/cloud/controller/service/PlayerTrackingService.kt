package io.ogwars.cloud.controller.service

import io.ogwars.cloud.api.event.PermissionExpiryEvent
import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.api.event.PlayerConnectEvent
import io.ogwars.cloud.api.event.PlayerDisconnectEvent
import io.ogwars.cloud.api.event.PlayerSwitchEvent
import io.ogwars.cloud.api.model.PermissionConfig
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.model.PlayerDocument
import io.ogwars.cloud.controller.redis.PlayerRedisRepository
import io.ogwars.cloud.controller.repository.PermissionGroupRepository
import io.ogwars.cloud.controller.repository.PlayerRepository
import io.ogwars.cloud.controller.repository.WebUserRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PlayerTrackingService(
    private val playerRepository: PlayerRepository,
    private val permissionGroupRepository: PermissionGroupRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val kafkaTemplate: KafkaTemplate<String, PermissionUpdateEvent>,
    private val webUserRepository: WebUserRepository
) {

    fun handleConnect(event: PlayerConnectEvent) {
        val existing = playerRepository.findById(event.uuid).orElse(null)
        val defaultGroup = requireDefaultGroup()

        val player = if (existing == null) {
            val newPlayer = PlayerDocument(
                id = event.uuid,
                name = event.name,
                permission = buildPermanentPermission(defaultGroup.id),
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

        val group = permissionGroupRepository.findById(player.permission.group).orElse(defaultGroup)

        playerRedisRepository.saveSession(event.uuid, player.name, event.proxyId, group, player.permission.endMillis)
    }

    fun handleDisconnect(event: PlayerDisconnectEvent) {
        playerRedisRepository.deleteSession(event.uuid)
    }

    fun handleSwitch(event: PlayerSwitchEvent) {
        playerRedisRepository.updateServerId(event.uuid, event.serverId)
    }

    fun handlePermissionUpdate(event: PermissionUpdateEvent) {
        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val group = permissionGroupRepository.findById(player.permission.group).orElse(null) ?: return

        if (playerRedisRepository.isOnline(event.uuid)) {
            playerRedisRepository.updatePermissions(event.uuid, group, player.permission.endMillis)
        }
    }

    fun handlePermissionExpiry(event: PermissionExpiryEvent) {
        val defaultGroup = requireDefaultGroup()

        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val updated = player.copy(
            permission = buildPermanentPermission(defaultGroup.id)
        )

        playerRepository.save(updated)

        if (playerRedisRepository.isOnline(event.uuid)) {
            playerRedisRepository.updatePermissions(event.uuid, defaultGroup, PERMANENT_PERMISSION_END_MILLIS)
        }

        val updateEvent = PermissionUpdateEvent(
            uuid = event.uuid,
            groupId = defaultGroup.id,
            groupName = defaultGroup.name,
            permissions = defaultGroup.permissions,
            display = defaultGroup.display,
            weight = defaultGroup.weight,
            permissionEndMillis = PERMANENT_PERMISSION_END_MILLIS,
            updatedBy = PERMISSION_EXPIRY_UPDATED_BY
        )

        kafkaTemplate.send(KafkaConfig.PERMISSION_UPDATE, event.uuid, updateEvent)
    }

    private fun requireDefaultGroup() = permissionGroupRepository.findByDefaultTrue()
        ?: throw IllegalStateException(NO_DEFAULT_PERMISSION_GROUP_MESSAGE)

    private fun buildPermanentPermission(groupId: String): PermissionConfig {
        return PermissionConfig(group = groupId)
    }

    companion object {
        private const val NO_DEFAULT_PERMISSION_GROUP_MESSAGE = "No default permission group configured"
        private const val PERMANENT_PERMISSION_END_MILLIS = -1L
        private const val PERMISSION_EXPIRY_UPDATED_BY = "expiry"
    }
}
