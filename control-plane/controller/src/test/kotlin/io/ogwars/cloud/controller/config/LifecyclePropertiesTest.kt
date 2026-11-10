package io.ogwars.cloud.controller.config

import io.ogwars.cloud.domain.server.LifecycleTimeouts
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class LifecyclePropertiesTest {
    @EnableConfigurationProperties(LifecycleProperties::class)
    private class Enabled

    // The chart passes only the timeouts an install names (D-0179).
    @Test
    fun `named timeouts bind and the others keep their defaults`() {
        ApplicationContextRunner()
            .withUserConfiguration(Enabled::class.java)
            .withPropertyValues(
                "ogcloud.controller.lifecycle.timeouts.starting=7m",
                "ogcloud.controller.lifecycle.timeouts.transfer-grace=45s",
            ).run { context ->
                context.getBean(LifecycleProperties::class.java).timeouts shouldBe
                    LifecycleTimeouts(starting = Duration.ofMinutes(7), transferGrace = Duration.ofSeconds(45))
            }
    }
}
