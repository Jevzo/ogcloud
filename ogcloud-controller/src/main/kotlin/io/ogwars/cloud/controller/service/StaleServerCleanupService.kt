package io.ogwars.cloud.controller.service

import io.ogwars.cloud.controller.config.StaleServerCleanupProperties
import io.ogwars.cloud.controller.model.ServerDocument
import io.ogwars.cloud.controller.redis.ServerRedisRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class StaleServerCleanupService(
    private val staleServerCleanupProperties: StaleServerCleanupProperties,
    private val serverRedisRepository: ServerRedisRepository,
    private val serverLifecycleService: ServerLifecycleService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var cleanupEnabledAt: Instant? = null

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        cleanupEnabledAt = Instant.now().plus(staleServerCleanupProperties.startupGracePeriod)

        log.info(
            "Stale server cleanup armed: startupGracePeriod={}, cleanupEnabledAt={}",
            staleServerCleanupProperties.startupGracePeriod,
            cleanupEnabledAt,
        )
    }

    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    fun checkStaleServers() {
        val now = Instant.now()
        val enabledAt = cleanupEnabledAt
        if (enabledAt == null || now.isBefore(enabledAt)) {
            return
        }

        serverRedisRepository
            .findStartingServersStartedBefore(now.minus(STARTING_TIMEOUT))
            .forEach { server -> handleStartingServer(server, now) }

        serverRedisRepository
            .findRunningServersWithHeartbeatBefore(now.minus(HEARTBEAT_TIMEOUT))
            .forEach { server -> handleRunningServer(server, now) }
    }

    private fun handleStartingServer(
        server: ServerDocument,
        now: Instant,
    ) {
        val startedAt = server.startedAt ?: return
        if (!isPastDeadline(startedAt, now, STARTING_TIMEOUT)) {
            return
        }

        log.warn(
            "Server stuck in STARTING state, cleaning up: id={}, group={}, startedAt={}",
            server.id,
            server.group,
            startedAt,
        )

        serverLifecycleService.cleanupFailedServer(
            server,
            "No heartbeat within ${STARTING_TIMEOUT.seconds}s of start",
        )
    }

    private fun handleRunningServer(
        server: ServerDocument,
        now: Instant,
    ) {
        val lastHeartbeat = server.lastHeartbeat ?: return
        if (!isPastDeadline(lastHeartbeat, now, HEARTBEAT_TIMEOUT)) {
            return
        }

        log.warn(
            "Server missed heartbeat deadline, stopping: id={}, group={}, lastHeartbeat={}",
            server.id,
            server.group,
            lastHeartbeat,
        )

        serverLifecycleService.stopServer(server.id, "stale-heartbeat")
    }

    private fun isPastDeadline(
        referenceTime: Instant,
        now: Instant,
        timeout: Duration,
    ): Boolean = Duration.between(referenceTime, now) > timeout

    companion object {
        private const val CLEANUP_INTERVAL_MS = 15_000L
        private val STARTING_TIMEOUT: Duration = Duration.ofSeconds(90)
        private val HEARTBEAT_TIMEOUT: Duration = Duration.ofSeconds(30)
    }
}
