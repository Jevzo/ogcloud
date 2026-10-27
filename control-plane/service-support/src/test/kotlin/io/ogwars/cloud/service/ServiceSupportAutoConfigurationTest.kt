package io.ogwars.cloud.service

import io.ogwars.cloud.domain.common.IdGenerator
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ServiceSupportAutoConfigurationTest {
    private val runner =
        ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(ServiceSupportAutoConfiguration::class.java),
        )

    @Test
    fun `ids follow the clock and secrets are url-safe`() {
        val fixed = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC)
        runner.withBean(Clock::class.java, { fixed }).run { context ->
            val id = context.getBean(IdGenerator::class.java).next()
            (id.mostSignificantBits ushr TIMESTAMP_SHIFT) shouldBe fixed.millis()
            Regex("^[A-Za-z0-9_-]{43}$").matches(context.getBean(SecretGenerator::class.java).next()) shouldBe true
        }
    }

    private companion object {
        const val TIMESTAMP_SHIFT = 16
    }
}
