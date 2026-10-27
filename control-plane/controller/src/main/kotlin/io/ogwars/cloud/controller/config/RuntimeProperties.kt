package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("ogcloud.controller.runtime")
data class RuntimeProperties(
    /** OgCloud's own plugin jars, one `ogcloud-<platform>.jar` each, shipped in the controller image (D-0079). */
    val pluginsDir: Path = Path.of("/opt/ogcloud/plugins"),
    val syncInterval: Duration = Duration.ofMinutes(10),
    val downloadTimeout: Duration = Duration.ofMinutes(2),
)
