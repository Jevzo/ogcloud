package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ogcloud.server-cleanup")
data class StaleServerCleanupProperties(
    val startupGracePeriod: Duration = DEFAULT_STARTUP_GRACE_PERIOD,
) {
    init {
        require(!startupGracePeriod.isNegative) {
            "ogcloud.server-cleanup.startup-grace-period must be zero or greater"
        }
    }

    companion object {
        private val DEFAULT_STARTUP_GRACE_PERIOD: Duration = Duration.ofSeconds(45)
    }
}
