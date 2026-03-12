package io.ogwars.cloud.common.kafka

data class KafkaConsumerRecoverySettings(
    val restartInitialBackoffMs: Long,
    val restartMaxBackoffMs: Long,
    val restartJitterMs: Long,
) {
    init {
        require(restartInitialBackoffMs > 0) { "restartInitialBackoffMs must be greater than zero" }
        require(restartMaxBackoffMs >= restartInitialBackoffMs) {
            "restartMaxBackoffMs must be greater than or equal to restartInitialBackoffMs"
        }
        require(restartJitterMs >= 0) { "restartJitterMs must be zero or greater" }
    }
}
