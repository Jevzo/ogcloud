package io.ogwars.cloud.livestate

import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.LockManager
import io.ogwars.cloud.domain.store.ServerStateStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveStateAutoConfigurationTest {
    private val container = valkeyContainer().also { it.start() }
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LiveStateAutoConfiguration::class.java))
            .withBean(IdGenerator::class.java, { IdGenerator { UUID.randomUUID() } })
            .withPropertyValues(
                "ogcloud.valkey.host=${container.host}",
                "ogcloud.valkey.port=${container.getMappedPort(VALKEY_PORT)}",
            )

    @AfterAll
    fun stop() {
        container.stop()
    }

    @Test
    fun `exposes every live-state port on the configured Valkey`() {
        runner.run { context ->
            context.getBean(ServerStateStore::class.java).listAll() shouldBe emptyList()
            context
                .getBean(
                    LockManager::class.java,
                ).tryAcquire("auto-config", Duration.ofSeconds(5))
                ?.use { it.name } shouldBe
                "auto-config"
            context.getBean(CrashFailureStore::class.java).isPaused(GroupId("lobby")) shouldBe false
        }
    }

    @Test
    fun `requires exactly one of host and sentinel`() {
        shouldThrow<IllegalArgumentException> { ValkeyProperties() }
        shouldThrow<IllegalArgumentException> {
            ValkeyProperties(host = "valkey", sentinel = ValkeyProperties.Sentinel("ogcloud", listOf("s:26379")))
        }
        ValkeyProperties(
            sentinel = ValkeyProperties.Sentinel("ogcloud", listOf("s1:26379", "s2:26379")),
        ).toSettings().topology shouldBe
            ValkeyTopology.Sentinel("ogcloud", listOf(HostAndPort("s1", 26379), HostAndPort("s2", 26379)))
    }
}
