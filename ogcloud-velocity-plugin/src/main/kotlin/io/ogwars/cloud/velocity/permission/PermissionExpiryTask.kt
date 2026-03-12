package io.ogwars.cloud.velocity.permission

import io.ogwars.cloud.common.event.PermissionExpiryEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.velocity.kafka.KafkaSendDispatcher
import io.ogwars.cloud.velocity.network.NetworkState
import com.google.gson.Gson
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PermissionExpiryTask(
    private val permissionCache: PermissionCache,
    private val kafkaSendDispatcher: KafkaSendDispatcher,
    private val networkState: NetworkState,
    private val logger: Logger,
) {
    private val gson = Gson()
    private val notifiedExpirations = ConcurrentHashMap<UUID, Long>()
    private lateinit var scheduler: ScheduledExecutorService

    fun start() {
        scheduler =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "ogcloud-perm-expiry").apply { isDaemon = true }
            }
        scheduler.scheduleAtFixedRate(::checkExpiry, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS)
        logger.info("Permission expiry task started (interval={}s)", CHECK_INTERVAL_SECONDS)
    }

    fun stop() {
        if (::scheduler.isInitialized) {
            scheduler.shutdown()
        }
    }

    private fun checkExpiry() {
        if (!networkState.permissionSystemEnabled) {
            return
        }

        try {
            val now = System.currentTimeMillis()
            val activePlayers = mutableSetOf<UUID>()

            for ((uuid, cached) in permissionCache.getAllCachedPlayers()) {
                activePlayers += uuid

                if (cached.permissionEndMillis == PERMANENT_PERMISSION_END || now <= cached.permissionEndMillis) {
                    notifiedExpirations.remove(uuid)
                    continue
                }

                val previousExpiry = notifiedExpirations.put(uuid, cached.permissionEndMillis)
                if (previousExpiry != cached.permissionEndMillis) {
                    kafkaSendDispatcher.dispatch(
                        KafkaSendDispatcher.Message(
                            topic = KafkaTopics.PERMISSION_EXPIRY,
                            key = uuid.toString(),
                            payload = gson.toJson(PermissionExpiryEvent(uuid = uuid.toString())),
                            type = KafkaSendDispatcher.MessageType.PERMISSION_EXPIRY,
                        ),
                    )
                    logger.info("Permission expired for player: uuid={}, group={}", uuid, cached.groupId)
                }
            }

            notifiedExpirations.keys.removeIf { it !in activePlayers }
        } catch (exception: Exception) {
            logger.error("Failed to check permission expiry", exception)
        }
    }

    companion object {
        private const val CHECK_INTERVAL_SECONDS = 60L
        private const val PERMANENT_PERMISSION_END = -1L
    }
}
