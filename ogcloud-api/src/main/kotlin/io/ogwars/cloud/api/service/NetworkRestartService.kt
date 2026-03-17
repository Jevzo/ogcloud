package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.exception.GroupRestartSyncInProgressException
import io.ogwars.cloud.api.exception.NetworkRestartSyncInProgressException
import io.ogwars.cloud.api.kafka.ServerRequestProducer
import io.ogwars.cloud.api.kafka.ServerStopProducer
import io.ogwars.cloud.api.model.GroupDocument
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.NetworkSettingsDocument
import io.ogwars.cloud.common.model.ServerState
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NetworkRestartService(
    private val mongoTemplate: MongoTemplate,
    private val groupRepository: GroupRepository,
    private val serverRedisRepository: ServerRedisRepository,
    private val serverRequestProducer: ServerRequestProducer,
    private val serverStopProducer: ServerStopProducer,
    private val restartSyncLockService: RestartSyncLockService,
    private val auditLogService: AuditLogService,
    private val groupOperationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestRestart() {
        val networkSettings = findNetworkSettings()
        if (!networkSettings.maintenance) {
            throw IllegalArgumentException("Network maintenance must be enabled before restart can be requested")
        }

        if (!groupRepository.existsById(networkSettings.defaultGroup)) {
            throw IllegalArgumentException(
                "Default group not found for network restart: ${networkSettings.defaultGroup}",
            )
        }

        val lockToken =
            restartSyncLockService.acquireNetworkRestartLock()
                ?: throw NetworkRestartSyncInProgressException("Network restart is already in progress")

        if (restartSyncLockService.hasAnyGroupRestartLockActive()) {
            restartSyncLockService.releaseNetworkRestartLock(lockToken)
            throw GroupRestartSyncInProgressException(
                "Network restart is blocked while a group restart is in progress",
            )
        }

        try {
            groupOperationTaskExecutor.execute {
                try {
                    restartNetwork(networkSettings.defaultGroup)
                } catch (ex: Exception) {
                    log.error("Async network restart failed: defaultGroup={}", networkSettings.defaultGroup, ex)
                } finally {
                    restartSyncLockService.releaseNetworkRestartLock(lockToken)
                }
            }
        } catch (ex: Exception) {
            restartSyncLockService.releaseNetworkRestartLock(lockToken)
            throw ex
        }

        auditLogService.logApiAction(
            action = "NETWORK_RESTART_REQUESTED",
            targetType = "NETWORK",
            targetId = "global",
            summary = "Requested asynchronous network restart",
            metadata = mapOf("defaultGroup" to networkSettings.defaultGroup),
        )
    }

    private fun restartNetwork(defaultGroupId: String) {
        val groups = groupRepository.findAll().sortedBy(GroupDocument::id)
        val defaultGroup =
            groups.firstOrNull { it.id == defaultGroupId }
                ?: throw IllegalStateException("Default group disappeared before network restart: $defaultGroupId")

        restartPhase(
            phase = "proxy",
            targets =
                groups
                    .filter { it.type == GroupType.PROXY }
                    .mapNotNull(::createRestartTarget),
        )

        if (defaultGroup.type != GroupType.PROXY) {
            createRestartTarget(defaultGroup)?.let { target ->
                restartPhase(phase = "default-group", targets = listOf(target))
            }
        }

        restartInBatches(
            phasePrefix = "dynamic-groups",
            targets =
                groups
                    .filter { it.type == GroupType.DYNAMIC && it.id != defaultGroupId }
                    .mapNotNull(::createRestartTarget),
        )

        restartInBatches(
            phasePrefix = "static-groups",
            targets =
                groups
                    .filter { it.type == GroupType.STATIC && it.id != defaultGroupId }
                    .mapNotNull(::createRestartTarget),
        )
    }

    private fun restartInBatches(
        phasePrefix: String,
        targets: List<RestartTarget>,
    ) {
        targets.chunked(RESTART_BATCH_SIZE).forEachIndexed { index, batch ->
            restartPhase(
                phase = "$phasePrefix-batch-${index + 1}",
                targets = batch,
            )
        }
    }

    private fun restartPhase(
        phase: String,
        targets: List<RestartTarget>,
    ) {
        if (targets.isEmpty()) {
            return
        }

        log.info(
            "Starting network restart phase: phase={}, groups={}",
            phase,
            targets.map(RestartTarget::groupId),
        )

        stopTargets(phase, targets)

        val restartRequestedAt = Instant.now()
        targets.forEach(::requestReplacementServers)

        waitForHealthyTargets(phase, targets, restartRequestedAt)

        log.info(
            "Completed network restart phase: phase={}, groups={}",
            phase,
            targets.map(RestartTarget::groupId),
        )
    }

    private fun createRestartTarget(group: GroupDocument): RestartTarget? {
        val servers = serverRedisRepository.findByGroup(group.id)
        val activeServers = servers.filterNot(::isStoppedOrStopping)
        val healthyTargetCount = calculateHealthyTargetCount(group)

        if (activeServers.isEmpty() && healthyTargetCount == 0) {
            return null
        }

        return RestartTarget(
            groupId = group.id,
            healthyTargetCount = healthyTargetCount,
            stopTimeoutSeconds = calculateStopTimeoutSeconds(group),
        )
    }

    private fun stopTargets(
        phase: String,
        targets: List<RestartTarget>,
    ) {
        val stopRequestedIds = mutableSetOf<String>()
        val deadlinesByGroup =
            targets.associate { target ->
                target.groupId to Instant.now().plusSeconds(target.stopTimeoutSeconds)
            }

        while (true) {
            var allStopped = true

            targets.forEach { target ->
                val servers = serverRedisRepository.findByGroup(target.groupId)
                if (servers.isEmpty()) {
                    return@forEach
                }

                allStopped = false
                requestStopsForActiveServers(phase, target.groupId, servers, stopRequestedIds)

                if (Instant.now().isAfter(deadlinesByGroup.getValue(target.groupId))) {
                    throw IllegalStateException(
                        "Network restart phase '$phase' timed out stopping group '${target.groupId}': ${
                            servers.map(ServerDocument::id).sorted().joinToString(", ")
                        }",
                    )
                }
            }

            if (allStopped) {
                return
            }

            sleepForPoll("waiting for groups to stop in phase $phase")
        }
    }

    private fun requestStopsForActiveServers(
        phase: String,
        groupId: String,
        servers: List<ServerDocument>,
        stopRequestedIds: MutableSet<String>,
    ) {
        servers
            .filterNot(::isStoppedOrStopping)
            .forEach { server ->
                if (stopRequestedIds.add(server.id)) {
                    serverStopProducer.stopServer(server.id)
                    log.info(
                        "Requested server stop for network restart: phase={}, group={}, serverId={}, state={}",
                        phase,
                        groupId,
                        server.id,
                        server.state,
                    )
                }
            }
    }

    private fun requestReplacementServers(target: RestartTarget) {
        repeat(target.healthyTargetCount) {
            val serverId = serverRequestProducer.requestServer(target.groupId)
            log.info(
                "Requested replacement server for network restart: group={}, serverId={}",
                target.groupId,
                serverId,
            )
        }
    }

    private fun waitForHealthyTargets(
        phase: String,
        targets: List<RestartTarget>,
        restartRequestedAt: Instant,
    ) {
        val expectedTargets = targets.filter { it.healthyTargetCount > 0 }
        if (expectedTargets.isEmpty()) {
            return
        }

        val deadline = Instant.now().plusSeconds(HEALTH_WAIT_TIMEOUT_SECONDS)

        while (true) {
            val unhealthyTargets =
                expectedTargets.filterNot { target ->
                    isTargetHealthy(target, restartRequestedAt)
                }

            if (unhealthyTargets.isEmpty()) {
                return
            }

            if (Instant.now().isAfter(deadline)) {
                val stateSummary =
                    unhealthyTargets.joinToString("; ") { target ->
                        val states =
                            serverRedisRepository
                                .findByGroup(target.groupId)
                                .joinToString(", ") { server ->
                                    "${server.id}:${server.state}:startedAt=${server.startedAt}:lastHeartbeat=${server.lastHeartbeat}"
                                }

                        "${target.groupId}[expected=${target.healthyTargetCount}, actual=${healthyServerCount(
                            target,
                            restartRequestedAt,
                        )}, servers=$states]"
                    }

                throw IllegalStateException(
                    "Network restart phase '$phase' did not become healthy before timeout: $stateSummary",
                )
            }

            sleepForPoll("waiting for healthy heartbeats in phase $phase")
        }
    }

    private fun isTargetHealthy(
        target: RestartTarget,
        restartRequestedAt: Instant,
    ): Boolean = healthyServerCount(target, restartRequestedAt) >= target.healthyTargetCount

    private fun healthyServerCount(
        target: RestartTarget,
        restartRequestedAt: Instant,
    ): Int =
        serverRedisRepository
            .findByGroup(target.groupId)
            .count { server ->
                server.state == ServerState.RUNNING &&
                    server.startedAt?.let { !it.isBefore(restartRequestedAt) } == true &&
                    server.lastHeartbeat?.let { !it.isBefore(restartRequestedAt) } == true
            }

    private fun calculateHealthyTargetCount(group: GroupDocument): Int = group.scaling.minOnline

    private fun calculateStopTimeoutSeconds(group: GroupDocument): Long {
        val requestedDrainSeconds = group.drainTimeoutSeconds.toLong() + DELETE_TIMEOUT_BUFFER_SECONDS
        return requestedDrainSeconds.coerceAtLeast(MIN_STOP_WAIT_SECONDS)
    }

    private fun isStoppedOrStopping(server: ServerDocument): Boolean =
        server.state == ServerState.STOPPING || server.state == ServerState.STOPPED

    private fun sleepForPoll(context: String) {
        try {
            Thread.sleep(POLL_INTERVAL_MS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while $context", ex)
        }
    }

    private fun findNetworkSettings(): NetworkSettingsDocument =
        mongoTemplate.findById<NetworkSettingsDocument>(NETWORK_SETTINGS_ID, NETWORK_SETTINGS_COLLECTION)
            ?: NetworkSettingsDocument()

    private data class RestartTarget(
        val groupId: String,
        val healthyTargetCount: Int,
        val stopTimeoutSeconds: Long,
    )

    companion object {
        private const val NETWORK_SETTINGS_COLLECTION = "network_settings"
        private const val NETWORK_SETTINGS_ID = "global"
        private const val RESTART_BATCH_SIZE = 10
        private const val POLL_INTERVAL_MS = 1_000L
        private const val DELETE_TIMEOUT_BUFFER_SECONDS = 45L
        private const val MIN_STOP_WAIT_SECONDS = 30L
        private const val HEALTH_WAIT_TIMEOUT_SECONDS = 180L
    }
}
