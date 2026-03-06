package io.ogwars.cloud.controller.service

import io.ogwars.cloud.api.model.GameState
import io.ogwars.cloud.api.model.GroupType
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.controller.model.GroupDocument
import io.ogwars.cloud.controller.model.ServerDocument
import io.ogwars.cloud.controller.redis.ServerRedisRepository
import io.ogwars.cloud.controller.repository.GroupRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class AutoscalerService(
    private val groupRepository: GroupRepository,
    private val serverRedisRepository: ServerRedisRepository,
    private val serverLifecycleService: ServerLifecycleService,
    private val scalingLogService: ScalingLogService
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val lastScaleAction = ConcurrentHashMap<String, Instant>()

    @Scheduled(fixedRate = EVALUATION_INTERVAL_MS)
    fun evaluate() {
        serverLifecycleService.checkDrainTimeouts()

        groupRepository.findAll().forEach(::evaluateGroupSafely)
    }

    fun evaluateGroupNow(groupId: String) {
        val group = groupRepository.findById(groupId).orElse(null) ?: return
        evaluateGroupSafely(group, onDemand = true)
    }

    private fun evaluateGroup(group: GroupDocument) {
        val servers = serverRedisRepository.findByGroup(group.id)

        when (group.type) {
            GroupType.PROXY -> evaluateProxyGroup(group, servers)
            GroupType.DYNAMIC -> evaluateDynamicGroup(group, servers)
            GroupType.STATIC -> evaluateStaticGroup(group, servers)
        }
    }

    private fun evaluateProxyGroup(group: GroupDocument, servers: List<ServerDocument>) {
        if (shouldSkipScaling(group)) return

        val runningServers = servers.filter { it.state == ServerState.RUNNING }
        val runningCount = runningServers.size
        val pendingCount = servers.count(::isPendingServer)
        val drainingProxies = servers.filter { it.state == ServerState.DRAINING }
        val totalActive = runningCount + pendingCount + drainingProxies.size

        val availableCount = runningCount + pendingCount
        if (availableCount < group.scaling.minOnline && enforceProxyMinOnline(
                group,
                runningCount,
                pendingCount,
                drainingProxies,
                totalActive,
                availableCount
            )
        ) {
            return
        }

        val ratio = calculateLoadRatio(runningServers.sumOf(ServerDocument::playerCount), runningCount, group) ?: return

        if (handleProxyScaleUp(group, ratio, drainingProxies, totalActive)) {
            return
        }

        handleProxyScaleDown(group, ratio, runningServers, runningCount)
    }

    private fun evaluateDynamicGroup(group: GroupDocument, servers: List<ServerDocument>) {
        if (shouldSkipScaling(group)) return

        val lobbyServers = servers.filter { it.state == ServerState.RUNNING && it.gameState == GameState.LOBBY }
        val lobbyCount = lobbyServers.size
        val pendingCount = servers.count(::isPendingServer)
        val totalActive = servers.count { it.state != ServerState.STOPPED }

        if (enforceDynamicMinOnline(group, lobbyCount, pendingCount, totalActive)) {
            return
        }

        val ratio = calculateLoadRatio(lobbyServers.sumOf(ServerDocument::playerCount), lobbyCount, group) ?: return
        if (ratio > group.scaling.scaleUpThreshold && totalActive < group.scaling.maxInstances) {
            log.info(
                "Dynamic group '{}': load-based scale-up (ratio={}, threshold={})",
                group.id, ratio, group.scaling.scaleUpThreshold
            )

            requestServerForScaling(
                group,
                LOAD_REASON,
                "Started server due to load ratio=$ratio above threshold=${group.scaling.scaleUpThreshold}"
            )

            recordScaleAction(group.id)
            return
        }

        handleDynamicScaleDown(group, ratio, lobbyServers, lobbyCount)
    }

    private fun evaluateStaticGroup(group: GroupDocument, servers: List<ServerDocument>) {
        if (group.maintenance) return

        if (group.scaling.maxInstances > 1) {
            log.warn("Static group '{}' has maxInstances={}, enforcing max-1", group.id, group.scaling.maxInstances)
        }

        val hasActiveServer = servers.any { it.state != ServerState.STOPPED }

        if (group.scaling.minOnline >= 1 && !hasActiveServer) {
            log.info("Static group '{}': no active server, starting one", group.id)

            requestServerForScaling(
                group,
                STATIC_REASON,
                "Started static server because no active instance existed"
            )
        }
    }

    private fun evaluateGroupSafely(group: GroupDocument, onDemand: Boolean = false) {
        try {
            evaluateGroup(group)
        } catch (e: Exception) {
            val mode = if (onDemand) " on-demand" else ""
            log.error("Error evaluating group {}{}: {}", group.id, mode, e.message, e)
        }
    }

    private fun shouldSkipScaling(group: GroupDocument): Boolean {
        return group.maintenance || isOnCooldown(group)
    }

    private fun isOnCooldown(group: GroupDocument): Boolean {
        val lastAction = lastScaleAction[group.id] ?: return false
        return java.time.Duration.between(lastAction, Instant.now()).seconds < group.scaling.cooldownSeconds
    }

    private fun isPendingServer(server: ServerDocument): Boolean {
        return server.state in PENDING_SERVER_STATES
    }

    private fun recordScaleAction(groupId: String) {
        lastScaleAction[groupId] = Instant.now()
    }

    private fun calculateLoadRatio(totalPlayers: Int, runningCount: Int, group: GroupDocument): Double? {
        if (runningCount == 0 || group.scaling.playersPerServer <= 0) {
            return null
        }

        val capacity = runningCount * group.scaling.playersPerServer

        return totalPlayers.toDouble() / capacity
    }

    private fun enforceProxyMinOnline(
        group: GroupDocument,
        runningCount: Int,
        pendingCount: Int,
        drainingProxies: List<ServerDocument>,
        totalActive: Int,
        availableCount: Int
    ): Boolean {
        val deficit = group.scaling.minOnline - availableCount
        val toUndrain = drainingProxies.take(deficit)

        toUndrain.forEach { proxy ->
            log.info("Proxy group '{}': undraining proxy {} instead of starting new instance", group.id, proxy.id)

            undrainServerForScaling(
                proxy,
                MIN_ONLINE_REASON,
                "Undrained proxy to satisfy minOnline=${group.scaling.minOnline}"
            )
        }

        val remaining = deficit - toUndrain.size
        if (remaining > 0 && totalActive + remaining <= group.scaling.maxInstances) {
            log.info(
                "Proxy group '{}': minOnline enforcement, starting {} proxies (running={}, pending={}, minOnline={})",
                group.id, remaining, runningCount, pendingCount, group.scaling.minOnline
            )

            repeat(remaining) {
                requestServerForScaling(
                    group,
                    MIN_ONLINE_REASON,
                    "Started proxy to satisfy minOnline=${group.scaling.minOnline} (running=$runningCount, pending=$pendingCount)"
                )
            }
        }

        recordScaleAction(group.id)

        return true
    }

    private fun handleProxyScaleUp(
        group: GroupDocument,
        ratio: Double,
        drainingProxies: List<ServerDocument>,
        totalActive: Int
    ): Boolean {
        if (ratio <= group.scaling.scaleUpThreshold) {
            return false
        }

        val drainingCandidate = drainingProxies.firstOrNull()
        if (drainingCandidate != null) {
            log.info(
                "Proxy group '{}': load-based scale-up, undraining proxy {} (ratio={}, threshold={})",
                group.id, drainingCandidate.id, ratio, group.scaling.scaleUpThreshold
            )

            undrainServerForScaling(
                drainingCandidate,
                LOAD_REASON,
                "Undrained proxy due to load ratio=$ratio above threshold=${group.scaling.scaleUpThreshold}"
            )
        } else if (totalActive < group.scaling.maxInstances) {
            log.info(
                "Proxy group '{}': load-based scale-up (ratio={}, threshold={})",
                group.id, ratio, group.scaling.scaleUpThreshold
            )

            requestServerForScaling(
                group,
                LOAD_REASON,
                "Started proxy due to load ratio=$ratio above threshold=${group.scaling.scaleUpThreshold}"
            )
        }

        recordScaleAction(group.id)

        return true
    }

    private fun handleProxyScaleDown(
        group: GroupDocument,
        ratio: Double,
        runningServers: List<ServerDocument>,
        runningCount: Int
    ) {
        if (ratio >= group.scaling.scaleDownThreshold || runningCount <= group.scaling.minOnline) {
            return
        }

        val candidate = runningServers.minByOrNull(ServerDocument::playerCount) ?: return

        log.info(
            "Proxy group '{}': scale-down, draining proxy {} (players={}, ratio={}, threshold={})",
            group.id, candidate.id, candidate.playerCount, ratio, group.scaling.scaleDownThreshold
        )

        drainServerForScaling(
            candidate,
            SCALE_DOWN_REASON,
            "Drained proxy with players=${candidate.playerCount} due to load ratio=$ratio below threshold=${group.scaling.scaleDownThreshold}"
        )

        recordScaleAction(group.id)
    }

    private fun enforceDynamicMinOnline(
        group: GroupDocument,
        lobbyCount: Int,
        pendingCount: Int,
        totalActive: Int
    ): Boolean {
        val availableLobbyCount = lobbyCount + pendingCount
        if (availableLobbyCount >= group.scaling.minOnline || totalActive >= group.scaling.maxInstances) {
            return false
        }

        val toStart = minOf(
            group.scaling.minOnline - availableLobbyCount,
            group.scaling.maxInstances - totalActive
        )

        log.info(
            "Dynamic group '{}': minOnline enforcement, starting {} servers (lobbies={}, pending={}, minOnline={})",
            group.id, toStart, lobbyCount, pendingCount, group.scaling.minOnline
        )

        repeat(toStart) {
            requestServerForScaling(
                group,
                MIN_ONLINE_REASON,
                "Started server to satisfy minOnline=${group.scaling.minOnline} (lobbies=$lobbyCount, pending=$pendingCount)"
            )
        }

        recordScaleAction(group.id)

        return true
    }

    private fun handleDynamicScaleDown(
        group: GroupDocument,
        ratio: Double,
        lobbyServers: List<ServerDocument>,
        lobbyCount: Int
    ) {
        if (ratio >= group.scaling.scaleDownThreshold) {
            return
        }

        val excessCount = lobbyCount - group.scaling.minOnline
        if (excessCount <= 0) {
            return
        }

        val emptyLobbies = lobbyServers.filter { it.playerCount == 0 }
            .sortedByDescending(ServerDocument::startedAt)

        if (emptyLobbies.isEmpty()) {
            return
        }

        emptyLobbies.take(minOf(excessCount, emptyLobbies.size))
            .forEach { server ->
                log.info(
                    "Dynamic group '{}': scale-down, draining empty lobby server {} (ratio={}, threshold={})",
                    group.id, server.id, ratio, group.scaling.scaleDownThreshold
                )

                drainServerForScaling(
                    server,
                    SCALE_DOWN_REASON,
                    "Drained empty lobby due to load ratio=$ratio below threshold=${group.scaling.scaleDownThreshold}"
                )
            }

        recordScaleAction(group.id)
    }

    private fun requestServerForScaling(group: GroupDocument, reason: String, details: String) {
        val server = serverLifecycleService.requestServer(group.id, reason)
        scalingLogService.logDecision(group.id, START_ACTION, reason, server.id, details)
    }

    private fun drainServerForScaling(server: ServerDocument, reason: String, details: String) {
        serverLifecycleService.drainServer(server.id, reason)

        val action = if (server.playerCount == 0) STOP_ACTION else DRAIN_ACTION
        scalingLogService.logDecision(server.group, action, reason, server.id, details)
    }

    private fun undrainServerForScaling(server: ServerDocument, reason: String, details: String) {
        serverLifecycleService.undrainServer(server.id)
        scalingLogService.logDecision(server.group, UNDRAIN_ACTION, reason, server.id, details)
    }

    companion object {
        private const val EVALUATION_INTERVAL_MS = 30_000L
        private const val MIN_ONLINE_REASON = "autoscaler-min-online"
        private const val LOAD_REASON = "autoscaler-load"
        private const val SCALE_DOWN_REASON = "autoscaler-scale-down"
        private const val STATIC_REASON = "autoscaler-static"
        private const val START_ACTION = "START"
        private const val STOP_ACTION = "STOP"
        private const val DRAIN_ACTION = "DRAIN"
        private const val UNDRAIN_ACTION = "UNDRAIN"
        private val PENDING_SERVER_STATES = setOf(
            ServerState.REQUESTED,
            ServerState.PREPARING,
            ServerState.STARTING
        )
    }
}
