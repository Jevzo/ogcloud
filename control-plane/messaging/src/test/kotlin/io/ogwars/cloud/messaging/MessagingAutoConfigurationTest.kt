package io.ogwars.cloud.messaging

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MessagingAutoConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration::class.java))
            .withBean(Clock::class.java, { Clock.fixed(Instant.EPOCH, ZoneOffset.UTC) })
            .withBean(IdGenerator::class.java, { IdGenerator { UUID(0, 1) } })
            .withPropertyValues("ogcloud.kafka.bootstrap-servers=kafka:9093", "ogcloud.kafka.client-id=controller")

    @Test
    fun `binds the settings and stamps events with the service name`() {
        runner.run { context ->
            context.getBean(KafkaSettings::class.java).clientId shouldBe "controller"
            context.getBean(EventMetadataFactory::class.java).create(Actor.system("test")).producer shouldBe
                "controller"
            context.getBean(EventPublisher::class.java)
        }
    }
}
