package io.ogwars.cloud.controller.config

import io.ogwars.cloud.domain.retention.RetentionSettings
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** The `retention:` values of the chart (D-0043). */
@ConfigurationProperties("ogcloud.controller.cleanup")
data class CleanupProperties(
    /** Reports what would be deleted without deleting anything. */
    val dryRun: Boolean = false,
    val interval: Duration = Duration.ofHours(1),
    val retention: RetentionSettings = RetentionSettings(),
    /** Rows deleted per statement and statements per run, so one run never holds long locks. */
    val batchSize: Int = 1000,
    val maxBatches: Int = 50,
    /** Unreferenced objects younger than this may still be in use (a pod downloading, an upload finishing). */
    val orphanGrace: Duration = Duration.ofDays(1),
)
