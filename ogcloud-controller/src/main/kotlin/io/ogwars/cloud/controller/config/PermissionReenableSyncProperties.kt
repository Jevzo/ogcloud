package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ogcloud.permission-reenable-sync")
data class PermissionReenableSyncProperties(
    val targetPlayersPerSecond: Int = 1000,
    val minBatchSize: Int = 100,
    val maxBatchSize: Int = 2000,
    val lockTtl: Duration = Duration.ofMinutes(5),
) {
    init {
        require(targetPlayersPerSecond > 0) {
            "ogcloud.permission-reenable-sync.target-players-per-second must be greater than zero"
        }
        require(minBatchSize > 0) { "ogcloud.permission-reenable-sync.min-batch-size must be greater than zero" }
        require(maxBatchSize >= minBatchSize) {
            "ogcloud.permission-reenable-sync.max-batch-size must be greater than or equal to min-batch-size"
        }
        require(!lockTtl.isZero && !lockTtl.isNegative) {
            "ogcloud.permission-reenable-sync.lock-ttl must be greater than zero"
        }
    }
}
