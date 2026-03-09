package io.ogwars.cloud.velocity.listener

import io.ogwars.cloud.api.event.PlayerConnectEvent
import io.ogwars.cloud.api.event.PlayerDisconnectEvent
import io.ogwars.cloud.api.event.PlayerSwitchEvent
import io.ogwars.cloud.velocity.kafka.KafkaSendDispatcher
import io.ogwars.cloud.velocity.network.NetworkState
import io.ogwars.cloud.velocity.permission.PermissionCache
import io.ogwars.cloud.velocity.redis.RedisManager
import io.ogwars.cloud.velocity.server.ServerRegistry
import com.google.gson.Gson
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ExecutorService

class PlayerConnectionListener(
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val publishExecutor: ExecutorService,
    private val redisManager: RedisManager,
    private val permissionCache: PermissionCache,
    private val networkState: NetworkState,
    private val serverRegistry: ServerRegistry,
    private val proxyServer: ProxyServer,
    private val proxyGroup: String,
    private val proxyMaxPlayers: Int,
    private val proxyId: String,
    private val logger: Logger,
) {
    private val gson = Gson()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    @Subscribe
    fun onLogin(event: LoginEvent): EventTask {
        val player = event.player
        val uuid = player.uniqueId

        return EventTask.async {
            loadPermissions(uuid)

            val hasMaintenanceBypass = hasMaintenanceBypass(uuid)

            when {
                networkState.maintenance && !hasMaintenanceBypass -> {
                    denyLogin(event, uuid, networkState.maintenanceKickMessage)
                }

                serverRegistry.isGroupInMaintenance(proxyGroup) && !hasMaintenanceBypass -> {
                    denyLogin(event, uuid, networkState.maintenanceKickMessage)
                }

                proxyServer.playerCount >= proxyMaxPlayers -> {
                    denyLogin(event, uuid, PROXY_FULL_MESSAGE)
                }

                else -> {
                    val connectPublished = publishConnectWithRetry(uuid.toString(), player.username)
                    if (!connectPublished) {
                        logger.error(
                            "Failed to publish player connect after retries: uuid={}, attempts={}, windowMs={}",
                            uuid,
                            CONNECT_RETRY_MAX_ATTEMPTS,
                            CONNECT_RETRY_WINDOW_MILLIS,
                        )
                        publishOnCurrentThread(
                            topic = TOPIC_DISCONNECT,
                            key = uuid.toString(),
                            payload = PlayerDisconnectEvent(uuid.toString(), proxyId),
                            messageType = KafkaSendDispatcher.MessageType.PLAYER_DISCONNECT,
                        )
                        runCatching {
                            player.disconnect(legacySerializer.deserialize(CONNECT_FAILURE_MESSAGE))
                        }.onFailure {
                            logger.warn("Failed to disconnect player after connect publish failure: uuid={}", uuid, it)
                        }
                        denyLogin(event, uuid, CONNECT_FAILURE_MESSAGE)
                    }
                }
            }
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val uuid = event.player.uniqueId

        permissionCache.removePlayer(uuid)

        publishAsync(
            topic = TOPIC_DISCONNECT,
            key = uuid.toString(),
            payload = PlayerDisconnectEvent(uuid.toString(), proxyId),
            messageType = KafkaSendDispatcher.MessageType.PLAYER_DISCONNECT,
        )
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val uuid = event.player.uniqueId
        val serverId =
            event.server.serverInfo.name
                .substringAfter("-")
        val previousServerId =
            event.previousServer
                .orElse(null)
                ?.serverInfo
                ?.name
                ?.substringAfter("-")

        publishAsync(
            topic = TOPIC_SWITCH,
            key = uuid.toString(),
            payload = PlayerSwitchEvent(uuid.toString(), serverId, previousServerId),
            messageType = KafkaSendDispatcher.MessageType.PLAYER_SWITCH,
        )
    }

    private fun loadPermissions(uuid: UUID) {
        if (!networkState.permissionSystemEnabled) {
            permissionCache.removePlayer(uuid)
            return
        }

        permissionCache.removePlayer(uuid)

        try {
            val session = redisManager.getPlayerData(uuid.toString())
            if (session != null) {
                permissionCache.cachePlayerFromRedis(uuid, session)
                return
            }

            cacheDefaultPermissions(uuid)
            logger.warn("No Redis permission session for player: uuid={}, using default group fallback", uuid)
        } catch (exception: Exception) {
            logger.error("Failed to load permission session from Redis for player: uuid={}", uuid, exception)
            cacheDefaultPermissions(uuid)
        }
    }

    private fun cacheDefaultPermissions(uuid: UUID) {
        permissionCache.getDefaultGroup()?.let {
            permissionCache.cachePlayer(uuid, it, DEFAULT_PERMISSION_END_MILLIS)
        }
    }

    private fun denyLogin(
        event: LoginEvent,
        uuid: UUID,
        message: String,
    ) {
        permissionCache.removePlayer(uuid)
        event.result = ResultedEvent.ComponentResult.denied(legacySerializer.deserialize(message))
    }

    private fun publishAsync(
        topic: String,
        key: String,
        payload: Any,
        messageType: KafkaSendDispatcher.MessageType,
    ) {
        try {
            publishExecutor.execute {
                publishOnCurrentThread(topic, key, payload, messageType)
            }
        } catch (exception: Exception) {
            logger.error(
                "Failed to hand off Kafka publish task: topic={}, key={}, type={}",
                topic,
                key,
                messageType,
                exception,
            )
        }
    }

    private fun publishOnCurrentThread(
        topic: String,
        key: String,
        payload: Any,
        messageType: KafkaSendDispatcher.MessageType,
    ) {
        kafkaSendDispatcher.dispatch(
            KafkaSendDispatcher.Message(
                topic = topic,
                key = key,
                payload = gson.toJson(payload),
                type = messageType,
            ),
        )
    }

    private fun publishConnectWithRetry(
        uuid: String,
        username: String,
    ): Boolean {
        val payload = gson.toJson(PlayerConnectEvent(uuid, username, proxyId))
        val startMillis = System.currentTimeMillis()

        for (attempt in 1..CONNECT_RETRY_MAX_ATTEMPTS) {
            val elapsedMillis = System.currentTimeMillis() - startMillis
            val remainingMillis = CONNECT_RETRY_WINDOW_MILLIS - elapsedMillis
            if (remainingMillis <= 0) {
                break
            }

            val result =
                kafkaSendDispatcher.dispatchAndWait(
                    KafkaSendDispatcher.Message(
                        topic = TOPIC_CONNECT,
                        key = uuid,
                        payload = payload,
                        type = KafkaSendDispatcher.MessageType.PLAYER_CONNECT,
                    ),
                    remainingMillis,
                )

            when (result) {
                KafkaSendDispatcher.DispatchResult.SUCCESS -> return true
                KafkaSendDispatcher.DispatchResult.FAILED -> {
                    logger.warn(
                        "Player connect publish attempt failed: uuid={}, attempt={}, maxAttempts={}",
                        uuid,
                        attempt,
                        CONNECT_RETRY_MAX_ATTEMPTS,
                    )
                }

                KafkaSendDispatcher.DispatchResult.TIMED_OUT -> {
                    logger.warn(
                        "Player connect publish timed out: uuid={}, attempt={}, windowMs={}",
                        uuid,
                        attempt,
                        CONNECT_RETRY_WINDOW_MILLIS,
                    )
                    return false
                }

                KafkaSendDispatcher.DispatchResult.INTERRUPTED -> {
                    logger.warn("Player connect publish interrupted: uuid={}, attempt={}", uuid, attempt)
                    return false
                }
            }

            if (attempt < CONNECT_RETRY_MAX_ATTEMPTS) {
                val updatedElapsedMillis = System.currentTimeMillis() - startMillis
                val updatedRemainingMillis = CONNECT_RETRY_WINDOW_MILLIS - updatedElapsedMillis
                if (updatedRemainingMillis <= 0) {
                    break
                }

                val backoffMillis = minOf(CONNECT_RETRY_BACKOFF_MILLIS, updatedRemainingMillis)
                try {
                    Thread.sleep(backoffMillis)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }

        return false
    }

    private fun hasMaintenanceBypass(uuid: UUID): Boolean {
        if (!networkState.permissionSystemEnabled) {
            return false
        }

        return permissionCache.hasPermission(uuid, MAINTENANCE_BYPASS_PERMISSION)
    }

    companion object {
        private const val TOPIC_CONNECT = "ogcloud.player.connect"
        private const val TOPIC_DISCONNECT = "ogcloud.player.disconnect"
        private const val TOPIC_SWITCH = "ogcloud.player.switch"
        private const val MAINTENANCE_BYPASS_PERMISSION = "ogcloud.maintenance.bypass"
        private const val DEFAULT_PERMISSION_END_MILLIS = -1L
        private const val PROXY_FULL_MESSAGE = "&cThis proxy is full."
        private const val CONNECT_FAILURE_MESSAGE =
            "&cConnection failed due to backend communication issues. Please retry."
        private const val CONNECT_RETRY_MAX_ATTEMPTS = 5
        private const val CONNECT_RETRY_WINDOW_MILLIS = 30_000L
        private const val CONNECT_RETRY_BACKOFF_MILLIS = 1_000L
    }
}
