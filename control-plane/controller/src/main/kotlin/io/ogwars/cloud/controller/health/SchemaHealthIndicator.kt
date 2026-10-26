package io.ogwars.cloud.controller.health

import io.ogwars.cloud.persistence.SchemaVersionProbe
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component

/** Keeps the controller unready until the API has migrated the schema it was built against (D-0009). */
@Component("schema")
class SchemaHealthIndicator(
    private val probe: SchemaVersionProbe,
) : HealthIndicator {
    override fun health(): Health {
        val applied = probe.appliedVersion()
        val builder = if ((applied ?: 0) >= probe.requiredVersion) Health.up() else Health.outOfService()
        return builder.withDetail("required", probe.requiredVersion).withDetail("applied", applied ?: "none").build()
    }
}
