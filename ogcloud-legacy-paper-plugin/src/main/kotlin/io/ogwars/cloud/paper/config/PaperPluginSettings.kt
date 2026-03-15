package io.ogwars.cloud.paper.config

import io.ogwars.cloud.common.kafka.KafkaConsumerRecoverySettings

data class PaperPluginSettings(
    val serverId: String,
    val groupName: String,
    val groupType: String,
    val configuredMaxPlayers: Int,
    val kafkaBrokers: String,
    val redisHost: String,
    val redisPort: Int,
    val apiUrl: String,
    val apiEmail: String,
    val apiPassword: String,
    val kafkaConsumerRecoverySettings: KafkaConsumerRecoverySettings,
) {
    companion object {
        fun fromEnvironment(defaultMaxPlayers: Int): PaperPluginSettings =
            PaperPluginSettings(
                serverId = requiredEnv("OGCLOUD_SERVER_ID"),
                groupName = requiredEnv("OGCLOUD_GROUP"),
                groupType = System.getenv("OGCLOUD_GROUP_TYPE") ?: DEFAULT_GROUP_TYPE,
                configuredMaxPlayers =
                    System
                        .getenv("OGCLOUD_MAX_PLAYERS")
                        ?.toIntOrNull()
                        ?.coerceAtLeast(MIN_MAX_PLAYERS) ?: defaultMaxPlayers,
                kafkaBrokers = System.getenv("KAFKA_BROKERS") ?: DEFAULT_KAFKA_BROKERS,
                redisHost = System.getenv("REDIS_HOST") ?: DEFAULT_REDIS_HOST,
                redisPort = System.getenv("REDIS_PORT")?.toIntOrNull() ?: DEFAULT_REDIS_PORT,
                apiUrl = System.getenv("OGCLOUD_API_URL") ?: DEFAULT_API_URL,
                apiEmail = requiredEnv("OGCLOUD_API_EMAIL"),
                apiPassword = requiredEnv("OGCLOUD_API_PASSWORD"),
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

        private fun requiredEnv(name: String): String = System.getenv(name) ?: error("$name not set")

        private fun envLong(
            name: String,
            defaultValue: Long,
        ): Long = System.getenv(name)?.toLongOrNull() ?: defaultValue

        private const val DEFAULT_GROUP_TYPE = "DYNAMIC"
        private const val DEFAULT_KAFKA_BROKERS = "kafka.ogcloud.svc.cluster.local:9092"
        private const val DEFAULT_REDIS_HOST = "redis.ogcloud.svc.cluster.local"
        private const val DEFAULT_REDIS_PORT = 6379
        private const val DEFAULT_API_URL = "http://api.ogcloud.svc.cluster.local:8080"
        private const val MIN_MAX_PLAYERS = 1
        private const val DEFAULT_KAFKA_CONSUMER_RESTART_INITIAL_BACKOFF_MS = 1000L
        private const val DEFAULT_KAFKA_CONSUMER_RESTART_MAX_BACKOFF_MS = 30000L
        private const val DEFAULT_KAFKA_CONSUMER_RESTART_JITTER_MS = 500L
    }
}
