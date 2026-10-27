package io.ogwars.cloud.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class ServiceDefaultsTest {
    @Test
    fun `defaults apply unless a service sets its own value`() {
        val environment = StandardEnvironment()
        environment.propertySources.addFirst(
            MapPropertySource(
                "service",
                mapOf("logging.structured.format.console" to "logstash"),
            ),
        )

        ServiceDefaults().postProcessEnvironment(environment, SpringApplication())

        environment.getProperty("spring.threads.virtual.enabled") shouldBe "true"
        environment.getProperty("management.otlp.metrics.export.enabled") shouldBe "false"
        environment.getProperty("logging.structured.format.console") shouldBe "logstash"
    }
}
