package io.ogwars.cloud.controller

import io.ogwars.cloud.livestate.HostAndPort
import io.ogwars.cloud.livestate.VALKEY_PORT
import io.ogwars.cloud.livestate.ValkeyConnection
import io.ogwars.cloud.livestate.ValkeyConnections
import io.ogwars.cloud.livestate.ValkeySettings
import io.ogwars.cloud.livestate.ValkeyTopology
import io.ogwars.cloud.livestate.valkeyContainer

/** One Valkey per test JVM for service tests; each test flushes it first. */
object TestValkey {
    val connection: ValkeyConnection by lazy {
        val container = valkeyContainer().also { it.start() }
        ValkeyConnections
            .connect(
                ValkeySettings(
                    ValkeyTopology.Standalone(HostAndPort(container.host, container.getMappedPort(VALKEY_PORT))),
                ),
            ).second
    }

    fun flush() {
        connection.sync().flushall()
    }
}
