package io.ogwars.cloud.velocity.config

import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings
import java.util.*

data class VelocityPluginSettings(
    val kafkaBrokers: String,
    val redisHost: String,
    val redisPort: Int,
    val mongoUri: String,
    val mongoDatabase: String,
    val proxyId: String,
    val proxyDisplayName: String,
    val proxyGroup: String,
    val defaultGroup: String,
    val apiUrl: String,
    val apiEmail: String,
    val apiPassword: String,
    val proxyMaxPlayers: Int,
    val proxyPodIp: String,
    val proxyPort: Int,
    val kafkaConsumerRecoverySettings: KafkaConsumerRecoverySettings,
) {
    companion object {
        fun fromEnvironment(): VelocityPluginSettings {
            val proxyId = System.getenv("OGCLOUD_PROXY_ID") ?: UUID.randomUUID().toString().take(PROXY_ID_LENGTH)

            return VelocityPluginSettings(
                kafkaBrokers = System.getenv("KAFKA_BROKERS") ?: DEFAULT_KAFKA_BROKERS,
                redisHost = System.getenv("REDIS_HOST") ?: DEFAULT_REDIS_HOST,
                redisPort = System.getenv("REDIS_PORT")?.toIntOrNull() ?: DEFAULT_REDIS_PORT,
                mongoUri = System.getenv("MONGODB_URI") ?: DEFAULT_MONGO_URI,
                mongoDatabase = System.getenv("MONGODB_DATABASE") ?: DEFAULT_MONGO_DATABASE,
                proxyId = proxyId,
                proxyDisplayName =
                    System.getenv("OGCLOUD_PROXY_DISPLAY_NAME") ?: "proxy-${
                        proxyId.take(PROXY_DISPLAY_ID_LENGTH)
                    }",
                proxyGroup = System.getenv("OGCLOUD_GROUP") ?: DEFAULT_PROXY_GROUP,
                defaultGroup = System.getenv("OGCLOUD_DEFAULT_GROUP") ?: DEFAULT_GROUP,
                apiUrl = System.getenv("OGCLOUD_API_URL") ?: DEFAULT_API_URL,
                apiEmail = requiredEnv("OGCLOUD_API_EMAIL"),
                apiPassword = requiredEnv("OGCLOUD_API_PASSWORD"),
                proxyMaxPlayers =
                    System.getenv("OGCLOUD_MAX_PLAYERS")?.toIntOrNull()?.coerceAtLeast(MIN_MAX_PLAYERS)
                        ?: Int.MAX_VALUE,
                proxyPodIp = System.getenv("OGCLOUD_PROXY_POD_IP") ?: DEFAULT_PROXY_POD_IP,
                proxyPort = System.getenv("OGCLOUD_PROXY_PORT")?.toIntOrNull() ?: DEFAULT_PROXY_PORT,
                kafkaConsumerRecoverySettings =
                    KafkaConsumerRecoverySettings(
                        restartInitialBackoffMs =
                            envLong(
                                "OGCLOUD_KAFKA_CONSUMER_RESTART_INITIAL_BACKOFF_MS",
                                DEFAULT_KAFKA_CONSUMER_RESTART_INITIAL_BACKOFF_MS,
                            ),
                        restartMaxBackoffMs =
                            envLong(
                                "OGCLOUD_KAFKA_CONSUMER_RESTART_MAX_BACKOFF_MS",
                                DEFAULT_KAFKA_CONSUMER_RESTART_MAX_BACKOFF_MS,
                            ),
                        restartJitterMs =
                            envLong(
                                "OGCLOUD_KAFKA_CONSUMER_RESTART_JITTER_MS",
                                DEFAULT_KAFKA_CONSUMER_RESTART_JITTER_MS,
                            ),
                    ),
            )
        }

        private fun requiredEnv(name: String): String = System.getenv(name) ?: error("$name must be configured")

        private fun envLong(
            name: String,
            defaultValue: Long,
        ): Long = System.getenv(name)?.toLongOrNull() ?: defaultValue

        private const val PROXY_ID_LENGTH = 5
        private const val PROXY_DISPLAY_ID_LENGTH = 6
        private const val MIN_MAX_PLAYERS = 1
        private const val DEFAULT_KAFKA_BROKERS = "kafka.ogcloud.svc.cluster.local:9092"
        private const val DEFAULT_REDIS_HOST = "redis.ogcloud.svc.cluster.local"
        private const val DEFAULT_REDIS_PORT = 6379
        private const val DEFAULT_MONGO_URI = "mongodb://mongodb.ogcloud.svc.cluster.local:27017"
        private const val DEFAULT_MONGO_DATABASE = "ogcloud"
        private const val DEFAULT_PROXY_GROUP = "proxy"
        private const val DEFAULT_GROUP = "lobby"
        private const val DEFAULT_API_URL = "http://api.ogcloud.svc.cluster.local:8080"
        private const val DEFAULT_PROXY_POD_IP = "localhost"
        private const val DEFAULT_PROXY_PORT = 25577
        private const val DEFAULT_KAFKA_CONSUMER_RESTART_INITIAL_BACKOFF_MS = 1000L
        private const val DEFAULT_KAFKA_CONSUMER_RESTART_MAX_BACKOFF_MS = 30000L
        private const val DEFAULT_KAFKA_CONSUMER_RESTART_JITTER_MS = 500L
    }
}
