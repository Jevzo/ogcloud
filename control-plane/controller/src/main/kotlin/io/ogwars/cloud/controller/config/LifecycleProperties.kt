package io.ogwars.cloud.controller.config

import io.ogwars.cloud.domain.crashloop.CrashLoopSettings
import io.ogwars.cloud.domain.server.LifecycleTimeouts
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("ogcloud.controller.lifecycle")
data class LifecycleProperties(
    val timeouts: LifecycleTimeouts = LifecycleTimeouts(),
    val crashLoop: CrashLoopSettings = CrashLoopSettings(),
    val provisioning: Provisioning = Provisioning(),
    val sweepInterval: Duration = Duration.ofSeconds(5),
    val reconcileInterval: Duration = Duration.ofSeconds(30),
) {
    /** Limits on pod creation (D-0075). */
    data class Provisioning(
        val podsPerRun: Int = 5,
        val interval: Duration = Duration.ofSeconds(1),
        val maxPreparing: Int = 30,
    ) {
        init {
            require(podsPerRun > 0 && maxPreparing > 0) { "podsPerRun and maxPreparing must be positive" }
        }
    }
}
