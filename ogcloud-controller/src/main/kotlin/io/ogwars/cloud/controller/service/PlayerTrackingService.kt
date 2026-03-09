package io.ogwars.cloud.controller.service

import io.ogwars.cloud.api.event.*
import io.ogwars.cloud.api.model.PermissionConfig
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.model.PermissionGroupDocument
import io.ogwars.cloud.controller.model.PlayerDocument
import io.ogwars.cloud.controller.redis.PlayerRedisRepository
import io.ogwars.cloud.controller.repository.PlayerRepository
import io.ogwars.cloud.controller.repository.WebUserRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant

typealias ApiPermissionGroupDocument = io.ogwars.cloud.api.model.PermissionGroupDocument

@Service
class PlayerTrackingService(
    private val playerRepository: PlayerRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val kafkaTemplate: KafkaTemplate<String, PermissionUpdateEvent>,
    private val webUserRepository: WebUserRepository,
    private val playerConnectRuntimeState: PlayerConnectRuntimeState,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleConnect(event: PlayerConnectEvent) {
        if (!playerConnectRuntimeState.tryStartConnect(event.uuid, event.proxyId)) {
            log.debug(
                "Skipping duplicated player connect event within dedupe window: uuid={}, proxyId={}",
                event.uuid,
                event.proxyId,
            )
            return
        }

        val existing = playerRepository.findById(event.uuid).orElse(null)
        val permissionSystemEnabled = isPermissionSystemEnabled()
        val defaultGroup = if (permissionSystemEnabled) requireDefaultGroup() else null

        val player =
            if (existing == null) {
                val newPlayer =
                    PlayerDocument(
                        id = event.uuid,
                        name = event.name,
                        permission = defaultGroup?.let { buildPermanentPermission(it.id) } ?: PermissionConfig(),
                        firstJoin = Instant.now(),
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
            resolved.player.permission.endMillis,
        )
    }

    fun handleDisconnect(event: PlayerDisconnectEvent) {
        playerConnectRuntimeState.resetConnectDedupe(event.uuid)
        playerRedisRepository.deleteSession(event.uuid)
    }

    fun handleSwitch(event: PlayerSwitchEvent) {
        playerRedisRepository.updateServerId(event.uuid, event.serverId)
    }

    fun handlePermissionUpdate(event: PermissionUpdateEvent) {
        if (!isPermissionSystemEnabled()) {
            return
        }

        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val defaultGroup = requireDefaultGroup()
        val resolved = resolvePermissionAssignment(player, defaultGroup)

        if (playerRedisRepository.isOnline(event.uuid)) {
            playerRedisRepository.updatePermissions(
                event.uuid,
                resolved.group,
                resolved.player.permission.endMillis,
            )
        }
    }

    fun handlePermissionExpiry(event: PermissionExpiryEvent) {
        if (!isPermissionSystemEnabled()) {
            return
        }

        val defaultGroup = requireDefaultGroup()

        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val updated =
            player.copy(
                permission = buildPermanentPermission(defaultGroup.id),
            )

        playerRepository.save(updated)

        if (playerRedisRepository.isOnline(event.uuid)) {
            playerRedisRepository.updatePermissions(event.uuid, defaultGroup, PERMANENT_PERMISSION_END_MILLIS)
        }

        publishPermissionUpdate(event.uuid, defaultGroup, PERMANENT_PERMISSION_END_MILLIS, PERMISSION_EXPIRY_UPDATED_BY)
    }

    fun handlePermissionGroupUpdated(event: PermissionGroupUpdatedEvent) {
        val group = event.group
        if (event.deleted || group == null) {
            playerConnectRuntimeState.removePermissionGroup(event.groupId)
            return
        }

        playerConnectRuntimeState.upsertPermissionGroup(group.toControllerPermissionGroupDocument())
    }

    fun handleDefaultPermissionGroupChanged(event: DefaultPermissionGroupChangedEvent) {
        playerConnectRuntimeState.markDefaultPermissionGroup(event.groupId)
    }

    fun updatePermissionSystemEnabled(enabled: Boolean) {
        playerConnectRuntimeState.updatePermissionSystemEnabled(enabled)
    }

    fun handlePermissionSystemEnabled() {
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
                NETWORK_FEATURE_UPDATED_BY,
            )
        }
    }

    fun warmupOnlinePlayerSessions() {
        val onlineUuids = playerRedisRepository.findOnlinePlayerUuids()
        if (onlineUuids.isEmpty()) {
            log.info("Online player session warmup skipped: no online players")
            return
        }

        val permissionSystemEnabled = isPermissionSystemEnabled()
        val defaultGroup = if (permissionSystemEnabled) requireDefaultGroup() else null
        val playersById = playerRepository.findAllById(onlineUuids).associateBy { it.id }

        var warmed = 0
        var missingPlayers = 0
        var rebuiltWithoutRuntimeSession = 0

        onlineUuids.forEach { uuid ->
            val player = playersById[uuid]
            if (player == null) {
                missingPlayers += 1
                return@forEach
            }

            val runtimeSession = playerRedisRepository.findPlayerData(uuid)
            val proxyId = runtimeSession?.proxyId ?: UNKNOWN_PROXY_ID

            if (runtimeSession == null) {
                rebuiltWithoutRuntimeSession += 1
            }

            if (!permissionSystemEnabled || defaultGroup == null) {
                playerRedisRepository.saveSession(uuid, player.name, proxyId)
                runtimeSession?.serverId?.let { playerRedisRepository.updateServerId(uuid, it) }
                warmed += 1
                return@forEach
            }

            val resolved = resolvePermissionAssignment(player, defaultGroup)
            playerRedisRepository.saveSession(
                uuid = uuid,
                name = resolved.player.name,
                proxyId = proxyId,
                group = resolved.group,
                permissionEndMillis = resolved.player.permission.endMillis,
            )
            runtimeSession?.serverId?.let { playerRedisRepository.updateServerId(uuid, it) }
            warmed += 1
        }

        log.info(
            "Online player session warmup completed: online={}, warmed={}, missingPlayers={}, rebuiltWithoutRuntimeSession={}",
            onlineUuids.size,
            warmed,
            missingPlayers,
            rebuiltWithoutRuntimeSession,
        )
    }

    private fun requireDefaultGroup(): PermissionGroupDocument =
        playerConnectRuntimeState.requireDefaultPermissionGroup()

    private fun resolvePermissionAssignment(
        player: PlayerDocument,
        defaultGroup: PermissionGroupDocument,
    ): ResolvedPermissionAssignment {
        val assignedGroup = playerConnectRuntimeState.findPermissionGroup(player.permission.group)
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
        updatedBy: String,
    ) {
        val updateEvent =
            PermissionUpdateEvent(
                uuid = uuid,
                groupId = group.id,
                groupName = group.name,
                permissions = group.permissions,
                display = group.display,
                weight = group.weight,
                permissionEndMillis = permissionEndMillis,
                updatedBy = updatedBy,
            )

        kafkaTemplate.send(KafkaConfig.PERMISSION_UPDATE, uuid, updateEvent)
    }

    private fun buildPermanentPermission(groupId: String): PermissionConfig = PermissionConfig(group = groupId)

    private fun isPermissionSystemEnabled(): Boolean = playerConnectRuntimeState.isPermissionSystemEnabled()

    private fun ApiPermissionGroupDocument.toControllerPermissionGroupDocument(): PermissionGroupDocument =
        PermissionGroupDocument(
            id = id,
            name = name,
            display = display,
            weight = weight,
            default = default,
            permissions = permissions,
        )

    private data class ResolvedPermissionAssignment(
        val player: PlayerDocument,
        val group: PermissionGroupDocument,
    )

    companion object {
        private const val PERMANENT_PERMISSION_END_MILLIS = -1L
        private const val PERMISSION_EXPIRY_UPDATED_BY = "expiry"
        private const val NETWORK_FEATURE_UPDATED_BY = "network-feature"
        private const val UNKNOWN_PROXY_ID = "unknown"
    }
}
