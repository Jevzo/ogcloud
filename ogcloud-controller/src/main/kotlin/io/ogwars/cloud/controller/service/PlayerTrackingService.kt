package io.ogwars.cloud.controller.service

import io.ogwars.cloud.common.event.*
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.model.PermissionConfig
import io.ogwars.cloud.common.model.PermissionGroupPermission
import io.ogwars.cloud.common.redis.RedisKeys
import io.ogwars.cloud.controller.config.PermissionReenableSyncProperties
import io.ogwars.cloud.controller.model.PermissionGroupDocument
import io.ogwars.cloud.controller.model.PlayerDocument
import io.ogwars.cloud.controller.redis.PlayerRedisRepository
import io.ogwars.cloud.controller.redis.PlayerRedisRepository.SessionUpdateOutcome
import io.ogwars.cloud.controller.repository.PlayerRepository
import io.ogwars.cloud.controller.repository.WebUserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.math.ceil
import io.ogwars.cloud.common.model.PermissionGroupDocument as CommonPermissionGroupDocument

@Service
class PlayerTrackingService(
    private val playerRepository: PlayerRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val kafkaTemplate: KafkaTemplate<String, PermissionUpdateEvent>,
    private val webUserRepository: WebUserRepository,
    private val playerConnectRuntimeState: PlayerConnectRuntimeState,
    private val redisTemplate: StringRedisTemplate,
    private val permissionReenableSyncProperties: PermissionReenableSyncProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val permissionMissingSessionNoopCount = AtomicLong(0)
    private val permissionVersionRejectedCount = AtomicLong(0)
    private val permissionRetryExhaustedCount = AtomicLong(0)
    private val switchMissingSessionNoopCount = AtomicLong(0)
    private val switchRetryExhaustedCount = AtomicLong(0)

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
            resolved.player.permission.version,
        )
    }

    fun handleDisconnect(event: PlayerDisconnectEvent) {
        playerConnectRuntimeState.resetConnectDedupe(event.uuid)
        playerRedisRepository.deleteSession(event.uuid)
    }

    fun handleSwitch(event: PlayerSwitchEvent) {
        val outcome = playerRedisRepository.updateServerId(event.uuid, event.serverId)
        handleSwitchRedisOutcome(event.uuid, event.serverId, outcome)
    }

    fun handlePermissionUpdate(event: PermissionUpdateEvent) {
        if (!isPermissionSystemEnabled()) {
            return
        }

        val outcome =
            playerRedisRepository.updatePermissions(
                uuid = event.uuid,
                groupId = event.groupId,
                display = event.display,
                weight = event.weight,
                permissions = event.permissions,
                permissionEndMillis = event.permissionEndMillis,
                permissionVersion = event.permissionVersion,
            )
        handlePermissionRedisOutcome(
            uuid = event.uuid,
            permissionVersion = event.permissionVersion,
            updatedBy = event.updatedBy,
            context = "permission-event",
            outcome = outcome,
        )
    }

    fun handlePermissionExpiry(event: PermissionExpiryEvent) {
        if (!isPermissionSystemEnabled()) {
            return
        }

        val defaultGroup = requireDefaultGroup()

        val player = playerRepository.findById(event.uuid).orElse(null) ?: return
        val nextPermissionVersion = player.permission.version + 1
        val updated =
            player.copy(
                permission = buildPermanentPermission(defaultGroup.id, nextPermissionVersion),
            )

        playerRepository.save(updated)

        val redisUpdateOutcome =
            playerRedisRepository.updatePermissions(
                uuid = event.uuid,
                group = defaultGroup,
                permissionEndMillis = PERMANENT_PERMISSION_END_MILLIS,
                permissionVersion = nextPermissionVersion,
            )
        handlePermissionRedisOutcome(
            uuid = event.uuid,
            permissionVersion = nextPermissionVersion,
            updatedBy = PERMISSION_EXPIRY_UPDATED_BY,
            context = "permission-expiry",
            outcome = redisUpdateOutcome,
        )

        publishPermissionUpdate(
            uuid = event.uuid,
            group = defaultGroup,
            permissionEndMillis = PERMANENT_PERMISSION_END_MILLIS,
            permissionVersion = nextPermissionVersion,
            updatedBy = PERMISSION_EXPIRY_UPDATED_BY,
        )
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
        val lockToken = UUID.randomUUID().toString()
        if (!acquirePermissionReenableSyncLock(lockToken)) {
            log.info("Permission re-enable sync skipped: lock already active")
            return
        }

        val startedAtNanos = System.nanoTime()
        var refreshed = 0
        var missingPlayers = 0
        var kafkaAttempts = 0
        val kafkaFailures = AtomicInteger(0)

        try {
            val defaultGroup = requireDefaultGroup()
            val onlineSnapshot = playerRedisRepository.findOnlinePlayerUuids().toList()

            if (onlineSnapshot.isEmpty()) {
                log.info("Permission re-enable sync skipped: no online players in snapshot")
                return
            }

            var index = 0

            while (index < onlineSnapshot.size) {
                val processed = refreshed + missingPlayers
                val batchSize = computeDynamicBatchSize(processed, startedAtNanos)
                val endExclusive = minOf(index + batchSize, onlineSnapshot.size)
                val batchUuids = onlineSnapshot.subList(index, endExclusive)
                index = endExclusive

                val playersById = playerRepository.findAllById(batchUuids).associateBy { it.id }
                val kafkaPublishFutures =
                    ArrayList<CompletableFuture<SendResult<String, PermissionUpdateEvent>>>(batchUuids.size)

                batchUuids.forEach { uuid ->
                    val player = playersById[uuid]
                    if (player == null) {
                        missingPlayers += 1
                        return@forEach
                    }

                    val resolved = resolvePermissionAssignment(player, defaultGroup)
                    val redisUpdateOutcome =
                        playerRedisRepository.updatePermissions(
                            uuid = uuid,
                            group = resolved.group,
                            permissionEndMillis = resolved.player.permission.endMillis,
                            permissionVersion = resolved.player.permission.version,
                        )
                    handlePermissionRedisOutcome(
                        uuid = uuid,
                        permissionVersion = resolved.player.permission.version,
                        updatedBy = NETWORK_FEATURE_UPDATED_BY,
                        context = "permission-reenable-sync",
                        outcome = redisUpdateOutcome,
                    )

                    kafkaAttempts += 1
                    val kafkaFuture =
                        publishPermissionUpdate(
                            uuid = uuid,
                            group = resolved.group,
                            permissionEndMillis = resolved.player.permission.endMillis,
                            permissionVersion = resolved.player.permission.version,
                            updatedBy = NETWORK_FEATURE_UPDATED_BY,
                            onFailure = { throwable ->
                                kafkaFailures.incrementAndGet()
                                log.warn(
                                    "Permission re-enable sync Kafka publish failed: uuid={}, reason={}",
                                    uuid,
                                    throwable.message ?: throwable.javaClass.simpleName,
                                )
                            },
                        )
                    kafkaPublishFutures += kafkaFuture
                    refreshed += 1
                }

                awaitKafkaPublishResults(kafkaPublishFutures)
                throttleIfAheadOfTargetRate(refreshed + missingPlayers, startedAtNanos)
            }

            val durationMillis = ((System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(1L)
            val effectivePlayersPerSecond =
                ((refreshed + missingPlayers).toDouble() * 1000.0) / durationMillis.toDouble()

            log.info(
                "Permission re-enable sync completed: snapshot={}, refreshed={}, missingPlayers={}, kafkaAttempts={}, kafkaFailures={}, durationMs={}, effectivePlayersPerSecond={}",
                onlineSnapshot.size,
                refreshed,
                missingPlayers,
                kafkaAttempts,
                kafkaFailures.get(),
                durationMillis,
                effectivePlayersPerSecond,
            )
        } finally {
            releasePermissionReenableSyncLock(lockToken)
        }
    }

    private fun computeDynamicBatchSize(
        processed: Int,
        startedAtNanos: Long,
    ): Int {
        val elapsedNanos = (System.nanoTime() - startedAtNanos).coerceAtLeast(1L)
        val elapsedSeconds = elapsedNanos.toDouble() / NANOS_PER_SECOND
        val expectedProcessed = permissionReenableSyncProperties.targetPlayersPerSecond.toDouble() * elapsedSeconds
        val backlog = ceil(expectedProcessed - processed.toDouble()).toInt()

        return backlog.coerceIn(
            permissionReenableSyncProperties.minBatchSize,
            permissionReenableSyncProperties.maxBatchSize,
        )
    }

    private fun throttleIfAheadOfTargetRate(
        processed: Int,
        startedAtNanos: Long,
    ) {
        if (processed <= 0) {
            return
        }

        val targetElapsedNanos =
            (
                (processed.toDouble() / permissionReenableSyncProperties.targetPlayersPerSecond.toDouble()) *
                    NANOS_PER_SECOND
            ).toLong()
        val elapsedNanos = System.nanoTime() - startedAtNanos
        val sleepNanos = targetElapsedNanos - elapsedNanos

        if (sleepNanos > 0) {
            LockSupport.parkNanos(sleepNanos)
        }
    }

    private fun acquirePermissionReenableSyncLock(lockToken: String): Boolean =
        redisTemplate.opsForValue().setIfAbsent(
            RedisKeys.PERMISSION_REENABLE_SYNC_LOCK_KEY,
            lockToken,
            permissionReenableSyncProperties.lockTtl,
        ) == true

    private fun releasePermissionReenableSyncLock(lockToken: String) {
        val currentLockToken = redisTemplate.opsForValue().get(RedisKeys.PERMISSION_REENABLE_SYNC_LOCK_KEY) ?: return
        if (currentLockToken != lockToken) {
            log.warn(
                "Permission re-enable sync lock ownership changed before release; expectedToken={}, actualToken={}",
                lockToken,
                currentLockToken,
            )
            return
        }

        redisTemplate.delete(RedisKeys.PERMISSION_REENABLE_SYNC_LOCK_KEY)
    }

    private fun awaitKafkaPublishResults(futures: List<CompletableFuture<SendResult<String, PermissionUpdateEvent>>>) {
        if (futures.isEmpty()) {
            return
        }

        try {
            CompletableFuture.allOf(*futures.toTypedArray()).join()
        } catch (_: Exception) {
        }
    }

    private fun handleSwitchRedisOutcome(
        uuid: String,
        serverId: String,
        outcome: SessionUpdateOutcome,
    ) {
        when (outcome) {
            SessionUpdateOutcome.UPDATED -> Unit
            SessionUpdateOutcome.MISSING -> {
                val totalMissing = switchMissingSessionNoopCount.incrementAndGet()
                log.debug(
                    "Switch Redis session update skipped for missing session: uuid={}, serverId={}, missingSessionCount={}",
                    uuid,
                    serverId,
                    totalMissing,
                )
            }

            SessionUpdateOutcome.VERSION_REJECTED -> {
                log.warn(
                    "Unexpected switch Redis session version rejection: uuid={}, serverId={}",
                    uuid,
                    serverId,
                )
            }

            SessionUpdateOutcome.RETRY_EXHAUSTED -> {
                val totalRetryExhausted = switchRetryExhaustedCount.incrementAndGet()
                log.warn(
                    "Switch Redis session update retry exhausted: uuid={}, serverId={}, retryExhaustedCount={}",
                    uuid,
                    serverId,
                    totalRetryExhausted,
                )
            }
        }
    }

    private fun handlePermissionRedisOutcome(
        uuid: String,
        permissionVersion: Long,
        updatedBy: String,
        context: String,
        outcome: SessionUpdateOutcome,
    ) {
        when (outcome) {
            SessionUpdateOutcome.UPDATED -> Unit
            SessionUpdateOutcome.MISSING -> {
                val totalMissing = permissionMissingSessionNoopCount.incrementAndGet()
                log.debug(
                    "Permission Redis session update skipped for missing session: context={}, uuid={}, permissionVersion={}, updatedBy={}, missingSessionCount={}",
                    context,
                    uuid,
                    permissionVersion,
                    updatedBy,
                    totalMissing,
                )
            }

            SessionUpdateOutcome.VERSION_REJECTED -> {
                val totalRejected = permissionVersionRejectedCount.incrementAndGet()
                log.debug(
                    "Permission Redis session update rejected by stale version gate: context={}, uuid={}, permissionVersion={}, updatedBy={}, versionRejectedCount={}",
                    context,
                    uuid,
                    permissionVersion,
                    updatedBy,
                    totalRejected,
                )
            }

            SessionUpdateOutcome.RETRY_EXHAUSTED -> {
                val totalRetryExhausted = permissionRetryExhaustedCount.incrementAndGet()
                log.warn(
                    "Permission Redis session update retry exhausted: context={}, uuid={}, permissionVersion={}, updatedBy={}, retryExhaustedCount={}",
                    context,
                    uuid,
                    permissionVersion,
                    updatedBy,
                    totalRetryExhausted,
                )
            }
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
                permissionVersion = resolved.player.permission.version,
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

        val reassignedPlayer =
            player.copy(
                permission = buildPermanentPermission(defaultGroup.id, player.permission.version + 1),
            )
        playerRepository.save(reassignedPlayer)

        return ResolvedPermissionAssignment(player = reassignedPlayer, group = defaultGroup)
    }

    private fun publishPermissionUpdate(
        uuid: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
        permissionVersion: Long,
        updatedBy: String,
        onFailure: ((Throwable) -> Unit)? = null,
    ): CompletableFuture<SendResult<String, PermissionUpdateEvent>> {
        val updateEvent =
            PermissionUpdateEvent(
                uuid = uuid,
                groupId = group.id,
                groupName = group.name,
                permissions = group.permissionValues,
                display = group.display,
                weight = group.weight,
                permissionEndMillis = permissionEndMillis,
                permissionVersion = permissionVersion,
                updatedBy = updatedBy,
            )

        val publishFuture = kafkaTemplate.send(KafkaTopics.PERMISSION_UPDATE, uuid, updateEvent)
        if (onFailure != null) {
            publishFuture.whenComplete { _, throwable ->
                if (throwable != null) {
                    onFailure(throwable)
                }
            }
        }

        return publishFuture
    }

    private fun buildPermanentPermission(
        groupId: String,
        version: Long = INITIAL_PERMISSION_VERSION,
    ): PermissionConfig = PermissionConfig(group = groupId, version = version)

    private fun isPermissionSystemEnabled(): Boolean = playerConnectRuntimeState.isPermissionSystemEnabled()

    private fun CommonPermissionGroupDocument.toControllerPermissionGroupDocument(): PermissionGroupDocument =
        PermissionGroupDocument(
            id = id,
            name = name,
            display = display,
            weight = weight,
            default = default,
            permissions = permissions.map { PermissionGroupPermission(perm = it) },
        )

    private data class ResolvedPermissionAssignment(
        val player: PlayerDocument,
        val group: PermissionGroupDocument,
    )

    companion object {
        private const val PERMANENT_PERMISSION_END_MILLIS = -1L
        private const val INITIAL_PERMISSION_VERSION = 0L
        private const val PERMISSION_EXPIRY_UPDATED_BY = "expiry"
        private const val NETWORK_FEATURE_UPDATED_BY = "network-feature"
        private const val UNKNOWN_PROXY_ID = "unknown"
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
