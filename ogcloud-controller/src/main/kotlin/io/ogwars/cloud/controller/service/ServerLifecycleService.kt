package io.ogwars.cloud.controller.service

import io.ogwars.cloud.api.event.*
import io.ogwars.cloud.api.model.GameState
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.controller.exception.GroupNotFoundException
import io.ogwars.cloud.controller.exception.ServerNotFoundException
import io.ogwars.cloud.controller.kafka.LifecycleEventProducer
import io.ogwars.cloud.controller.kafka.PlayerTransferProducer
import io.ogwars.cloud.controller.model.GroupDocument
import io.ogwars.cloud.controller.model.ServerDocument
import io.ogwars.cloud.controller.redis.ServerRedisRepository
import io.ogwars.cloud.controller.repository.GroupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
class ServerLifecycleService(
    private val serverRedisRepository: ServerRedisRepository,
    private val groupRepository: GroupRepository,
    private val kubernetesService: KubernetesService,
    private val templateService: TemplateService,
    private val lifecycleEventProducer: LifecycleEventProducer,
    private val playerTransferProducer: PlayerTransferProducer,
    private val networkSettingsService: NetworkSettingsService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestServer(
        group: String,
        requestedBy: String,
        preGeneratedId: String? = null,
    ): ServerDocument {
        val groupConfig = requireGroup(group)

        ensureTemplateExists(groupConfig)
        ensureGroupCanStartServer(groupConfig)

        val server = createRequestedServer(groupConfig, preGeneratedId)
        saveServer(server, publishLifecycle = true)

        log.info(
            "Server requested: id={}, displayName={}, group={}, requestedBy={}",
            server.id,
            server.displayName,
            group,
            requestedBy,
        )

        prepareServer(server, groupConfig)

        return server
    }

    private fun prepareServer(
        server: ServerDocument,
        groupConfig: GroupDocument,
    ) {
        val preparing = saveServer(server.copy(state = ServerState.PREPARING), publishLifecycle = true)

        kubernetesService.createServerPod(preparing, groupConfig)

        saveServer(
            preparing.copy(state = ServerState.STARTING, startedAt = Instant.now()),
            publishLifecycle = true,
        )
    }

    fun handleHeartbeat(event: ServerHeartbeatEvent) {
        val server = findServerOrWarn(event.serverId, "heartbeat") ?: return

        if (server.state == ServerState.STARTING) {
            handleStartingServerHeartbeat(server, event)
            return
        }

        saveServer(server.withHeartbeat(event))
    }

    fun handleProxyHeartbeat(event: ProxyHeartbeatEvent) {
        val server = findServerOrWarn(event.proxyId, "proxy heartbeat", "proxyId") ?: return

        if (server.state == ServerState.STARTING) {
            saveServer(
                server.withProxyHeartbeat(event).copy(
                    state = ServerState.RUNNING,
                    podIpRetries = 0,
                ),
                publishLifecycle = true,
            )

            log.info(
                "Proxy is now RUNNING: id={}, displayName={}, group={}, podIp={}",
                server.id,
                server.displayName,
                server.group,
                event.podIp,
            )
            return
        }

        saveServer(server.withProxyHeartbeat(event))
    }

    fun handleGameStateUpdate(event: GameStateUpdateEvent) {
        val server = findServerOrWarn(event.serverId, "game state update") ?: return

        if (server.type != GroupType.DYNAMIC) {
            log.warn("Received game state update for non-dynamic server: id={}, type={}", server.id, server.type)
            return
        }

        if (server.state != ServerState.RUNNING) {
            log.warn(
                "Ignoring game state update for server not in RUNNING state: id={}, state={}",
                server.id,
                server.state,
            )
            return
        }

        val updated = saveServer(server.copy(gameState = event.gameState))

        log.info(
            "Game state updated: id={}, group={}, {} -> {}",
            server.id,
            server.group,
            server.gameState,
            event.gameState,
        )

        if (event.gameState == GameState.ENDING) {
            transferPlayers(updated, "game-ended")
            drainServer(server.id, "game-ended")
        }
    }

    fun cleanupFailedServer(
        server: ServerDocument,
        reason: String,
    ) {
        publishLifecycleEvent(server.copy(state = ServerState.STOPPED))

        try {
            kubernetesService.deleteServerPod(server.podName)
        } catch (e: Exception) {
            log.error("Failed to delete pod during cleanup: podName={}", server.podName, e)
        }

        serverRedisRepository.delete(server.id, server.group)

        log.info(
            "Failed server cleaned up: id={}, group={}, reason={}",
            server.id,
            server.group,
            reason,
        )
    }

    fun undrainServer(serverId: String) {
        val server = requireServer(serverId)

        if (server.state != ServerState.DRAINING) {
            log.warn("Cannot undrain server not in DRAINING state: id={}, state={}", serverId, server.state)
            return
        }

        val running = server.copy(state = ServerState.RUNNING, drainingStartedAt = null)
        serverRedisRepository.save(running)
        publishLifecycleEvent(running)

        log.info("Server undrained, back to RUNNING: id={}, group={}", serverId, server.group)
    }

    fun drainServer(
        serverId: String,
        reason: String,
    ) {
        val server = requireServer(serverId)

        if (server.state != ServerState.RUNNING) {
            log.warn("Cannot drain server not in RUNNING state: id={}, state={}", serverId, server.state)
            return
        }

        if (server.playerCount == 0) {
            log.info("Server has 0 players, skipping drain and stopping: id={}, group={}", serverId, server.group)
            stopServer(serverId, reason)
            return
        }

        val groupConfig = groupRepository.findById(server.group).orElse(null)
        if (groupConfig != null && groupConfig.drainTimeoutSeconds == 0) {
            log.info("Drain timeout is 0, stopping immediately: id={}, group={}", serverId, server.group)
            stopServer(serverId, reason)
            return
        }

        val draining = server.copy(state = ServerState.DRAINING, drainingStartedAt = Instant.now())
        serverRedisRepository.save(draining)

        publishLifecycleEvent(draining)

        log.info("Server draining: id={}, group={}, reason={}", serverId, server.group, reason)
    }

    private fun resolveTransferTarget(server: ServerDocument): String? {
        val networkSettings = networkSettingsService.findGlobal()

        return when (server.type) {
            GroupType.DYNAMIC ->
                if (server.group ==
                    networkSettings.defaultGroup
                ) {
                    server.group
                } else {
                    networkSettings.defaultGroup
                }
            GroupType.STATIC -> networkSettings.defaultGroup
            GroupType.PROXY -> null
        }
    }

    fun gracefulStop(
        serverId: String,
        reason: String,
    ) {
        val server = requireServer(serverId)

        when (server.state) {
            ServerState.RUNNING -> drainServer(serverId, reason)

            ServerState.REQUESTED, ServerState.PREPARING, ServerState.STARTING ->
                stopServer(serverId, reason)

            ServerState.DRAINING ->
                log.info("Server already draining: id={}, group={}", serverId, server.group)

            ServerState.STOPPING, ServerState.STOPPED ->
                log.info("Server already stopping/stopped: id={}, group={}", serverId, server.group)
        }
    }

    fun checkDrainTimeouts() {
        val now = Instant.now()
        val drainTimeoutByGroup =
            groupRepository
                .findAll()
                .associate { group ->
                    group.id to group.drainTimeoutSeconds.toLong().coerceAtLeast(0L)
                }

        serverRedisRepository
            .findDrainingServersDueByGroup(
                drainCutoffByGroup =
                    drainTimeoutByGroup.mapValues { (_, timeoutSeconds) ->
                        now.minusSeconds(timeoutSeconds)
                    },
                fallbackCutoff = now.minusSeconds(DEFAULT_DRAIN_TIMEOUT_SECONDS),
            ).forEach { server ->
                handleDrainingServer(
                    server = server,
                    now = now,
                    timeoutSeconds = drainTimeoutByGroup[server.group] ?: DEFAULT_DRAIN_TIMEOUT_SECONDS,
                )
            }
    }

    fun killServer(
        serverId: String,
        reason: String,
    ) {
        val server = requireServer(serverId)

        kubernetesService.forceDeleteServerPod(server.podName)

        publishLifecycleEvent(server.copy(state = ServerState.STOPPED))
        serverRedisRepository.delete(server.id, server.group)

        log.info("Server killed: id={}, group={}, reason={}", serverId, server.group, reason)
    }

    fun stopServer(
        serverId: String,
        reason: String,
    ) {
        val server = requireServer(serverId)

        saveServer(server.copy(state = ServerState.STOPPING), publishLifecycle = true)

        kubernetesService.deleteServerPod(server.podName)

        publishLifecycleEvent(server.copy(state = ServerState.STOPPED))
        serverRedisRepository.delete(server.id, server.group)

        log.info("Server stopped: id={}, group={}, reason={}", serverId, server.group, reason)
    }

    private fun publishLifecycleEvent(server: ServerDocument) {
        val event =
            ServerLifecycleEvent(
                serverId = server.id,
                group = server.group,
                type = server.type,
                state = server.state,
                displayName = server.displayName,
                podName = server.podName,
                podIp = server.podIp,
                port = server.port,
            )

        lifecycleEventProducer.publishStateChange(event)
    }

    private fun handleStartingServerHeartbeat(
        server: ServerDocument,
        event: ServerHeartbeatEvent,
    ) {
        val podIp = kubernetesService.getPodIp(server.podName)
        if (podIp == null) {
            handleMissingPodIp(server)
            return
        }

        saveServer(
            server.withHeartbeat(event).copy(
                state = ServerState.RUNNING,
                podIp = podIp,
                podIpRetries = 0,
            ),
            publishLifecycle = true,
        )

        log.info(
            "Server is now RUNNING: id={}, displayName={}, group={}, podIp={}",
            server.id,
            server.displayName,
            server.group,
            podIp,
        )
    }

    private fun handleMissingPodIp(server: ServerDocument) {
        val retries = server.podIpRetries + 1

        if (retries >= MAX_POD_IP_RETRIES) {
            log.error(
                "Server failed to obtain pod IP after {} retries, marking as failed: id={}, group={}",
                retries,
                server.id,
                server.group,
            )

            cleanupFailedServer(server, "Pod IP unavailable after $retries retries")
            return
        }

        log.warn("Pod IP not yet available for server {}, retry {}/{}", server.id, retries, MAX_POD_IP_RETRIES)

        saveServer(
            server.copy(
                podIpRetries = retries,
                lastHeartbeat = Instant.now(),
            ),
        )
    }

    private fun handleDrainingServer(
        server: ServerDocument,
        now: Instant = Instant.now(),
        timeoutSeconds: Long? = null,
    ) {
        val drainingStartedAt = server.drainingStartedAt ?: return

        if (server.playerCount == 0) {
            log.info("Draining server is empty, stopping: id={}, group={}", server.id, server.group)
            stopServer(server.id, "drain-empty")
            return
        }

        if (server.type == GroupType.PROXY) {
            return
        }

        val timeout =
            timeoutSeconds
                ?: groupRepository
                    .findById(server.group)
                    .map { it.drainTimeoutSeconds.toLong() }
                    .orElse(DEFAULT_DRAIN_TIMEOUT_SECONDS)

        val elapsed = Duration.between(drainingStartedAt, now).seconds
        if (elapsed < timeout) {
            return
        }

        transferPlayers(server, "drain-timeout")

        if (elapsed >= timeout + TRANSFER_GRACE_SECONDS) {
            log.info("Transfer grace expired, force stopping: id={}, group={}", server.id, server.group)

            stopServer(server.id, "drain-force-timeout")
        }
    }

    private fun transferPlayers(
        server: ServerDocument,
        reason: String,
    ) {
        log.info("Transferring players away from server: id={}, group={}, reason={}", server.id, server.group, reason)

        playerTransferProducer.publishTransfer(
            PlayerTransferEvent(
                serverId = server.id,
                target = resolveTransferTarget(server),
                reason = reason,
            ),
        )
    }

    private fun findServerOrWarn(
        id: String,
        eventName: String,
        idLabel: String = "id",
    ): ServerDocument? {
        val server = serverRedisRepository.findById(id)

        if (server == null) {
            log.warn("Received {} for unknown server: {}={}", eventName, idLabel, id)
        }

        return server
    }

    private fun saveServer(
        server: ServerDocument,
        publishLifecycle: Boolean = false,
    ): ServerDocument {
        serverRedisRepository.save(server)

        if (publishLifecycle) {
            publishLifecycleEvent(server)
        }

        return server
    }

    private fun requireServer(serverId: String): ServerDocument =
        serverRedisRepository.findById(serverId)
            ?: throw ServerNotFoundException(serverId)

    private fun requireGroup(groupId: String): GroupDocument =
        groupRepository
            .findById(groupId)
            .orElseThrow { GroupNotFoundException(groupId) }

    private fun ensureTemplateExists(group: GroupDocument) {
        val templatePath = "${group.templatePath}/${group.templateVersion}"
        if (!templateService.templateExists(group.templateBucket, templatePath)) {
            throw IllegalStateException("Template not found for group ${group.id} at $templatePath")
        }
    }

    private fun ensureGroupCanStartServer(group: GroupDocument) {
        if (group.type != GroupType.STATIC) {
            return
        }

        val existing = serverRedisRepository.findByGroup(group.id)
        if (existing.any { it.state != ServerState.STOPPED }) {
            throw IllegalStateException("Static group ${group.id} already has a running instance")
        }
    }

    private fun createRequestedServer(
        group: GroupDocument,
        preGeneratedId: String?,
    ): ServerDocument {
        val serverId = preGeneratedId ?: generateServerId()
        return ServerDocument(
            id = serverId,
            group = group.id,
            type = group.type,
            displayName = "${group.id}-${serverId.take(DISPLAY_NAME_ID_LENGTH)}",
            state = ServerState.REQUESTED,
            gameState = if (group.type == GroupType.DYNAMIC) GameState.LOBBY else null,
            podName = "${group.id}-$serverId",
            templateVersion = group.templateVersion,
        )
    }

    private fun generateServerId(): String = UUID.randomUUID().toString().replace(UUID_DASH, "")

    private fun ServerDocument.withHeartbeat(event: ServerHeartbeatEvent): ServerDocument =
        copy(
            playerCount = event.playerCount,
            maxPlayers = event.maxPlayers,
            tps = event.tps,
            memoryUsedMb = event.memoryUsedMb,
            lastHeartbeat = Instant.now(),
        )

    private fun ServerDocument.withProxyHeartbeat(event: ProxyHeartbeatEvent): ServerDocument =
        copy(
            podIp = event.podIp,
            port = event.port,
            playerCount = event.playerCount,
            maxPlayers = event.maxPlayers,
            tps = NO_TPS,
            memoryUsedMb = event.memoryUsedMb,
            lastHeartbeat = Instant.now(),
        )

    companion object {
        private const val DISPLAY_NAME_ID_LENGTH = 6
        private const val MAX_POD_IP_RETRIES = 5
        private const val DEFAULT_DRAIN_TIMEOUT_SECONDS = 60L
        private const val NO_TPS = -1.0
        private const val TRANSFER_GRACE_SECONDS = 30L
        private const val UUID_DASH = "-"
    }
}
