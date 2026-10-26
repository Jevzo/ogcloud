package io.ogwars.cloud.livestate

import io.lettuce.core.RedisClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import java.time.Instant

val NOW: Instant = Instant.parse("2026-10-26T12:00:00Z")

/** One standalone Valkey per test class, flushed before each test. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ValkeyTest {
    private val container: GenericContainer<*> =
        valkeyContainer().also { it.start() }
    private val client: RedisClient
    protected val valkey: ValkeyConnection

    init {
        val (client, connection) =
            ValkeyConnections.connect(
                ValkeySettings(
                    ValkeyTopology.Standalone(HostAndPort(container.host, container.getMappedPort(VALKEY_PORT))),
                ),
            )
        this.client = client
        valkey = connection
    }

    @BeforeEach
    fun flush() {
        valkey.sync().flushall()
    }

    @AfterAll
    fun stop() {
        valkey.close()
        client.shutdown()
        container.stop()
    }
}
