package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.kafka.consumer")
data class ControllerKafkaConsumerProperties(
    val busy: ConsumerTier =
        ConsumerTier(
            concurrency = 6,
            maxPollRecords = 500,
        ),
    val light: ConsumerTier =
        ConsumerTier(
            concurrency = 3,
            maxPollRecords = 250,
        ),
    val single: ConsumerTier =
        ConsumerTier(
            concurrency = 1,
            maxPollRecords = 100,
        ),
    val maxPollIntervalMs: Int = 300000,
    val sessionTimeoutMs: Int = 15000,
    val heartbeatIntervalMs: Int = 5000,
    val pollTimeoutMs: Long = 1500,
    val retry: Retry = Retry(),
) {
    init {
        validateTier("busy", busy)
        validateTier("light", light)
        validateTier("single", single)

        require(maxPollIntervalMs > 0) { "ogcloud.kafka.consumer.max-poll-interval-ms must be greater than zero" }
        require(sessionTimeoutMs > 0) { "ogcloud.kafka.consumer.session-timeout-ms must be greater than zero" }
        require(heartbeatIntervalMs > 0) { "ogcloud.kafka.consumer.heartbeat-interval-ms must be greater than zero" }
        require(heartbeatIntervalMs < sessionTimeoutMs) {
            "ogcloud.kafka.consumer.heartbeat-interval-ms must be less than session-timeout-ms"
        }
        require(pollTimeoutMs > 0) { "ogcloud.kafka.consumer.poll-timeout-ms must be greater than zero" }
        require(retry.maxRetries >= 0) { "ogcloud.kafka.consumer.retry.max-retries must be zero or greater" }
        require(retry.backoffMs > 0) { "ogcloud.kafka.consumer.retry.backoff-ms must be greater than zero" }
    }

    private fun validateTier(
        tierName: String,
        tier: ConsumerTier,
    ) {
        require(tier.concurrency > 0) {
            "ogcloud.kafka.consumer.$tierName.concurrency must be greater than zero"
        }
        require(tier.maxPollRecords > 0) {
            "ogcloud.kafka.consumer.$tierName.max-poll-records must be greater than zero"
        }
    }

    data class ConsumerTier(
        val concurrency: Int,
        val maxPollRecords: Int,
    )

    data class Retry(
        val maxRetries: Long = 3,
        val backoffMs: Long = 1000,
    )
}
