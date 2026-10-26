package io.ogwars.cloud.livestate

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import io.lettuce.core.internal.HostAndPort as LettuceHostAndPort

class ValkeyConnectionTest {
    @Test
    fun `authenticates with an ACL user`() {
        GenericContainer(VALKEY_IMAGE)
            .withExposedPorts(VALKEY_PORT)
            .withCommand(
                "valkey-server",
                "--user",
                "default",
                "off",
                "--user",
                "ogcloud",
                "on",
                ">s3cret",
                "~*",
                "&*",
                "+@all",
            ).use { container ->
                container.start()
                val endpoint = HostAndPort(container.host, container.getMappedPort(VALKEY_PORT))

                withConnection(
                    ValkeySettings(ValkeyTopology.Standalone(endpoint), username = "ogcloud", password = "s3cret"),
                ) {
                    it.sync().ping() shouldBe "PONG"
                }
                shouldThrowAny {
                    withConnection(
                        ValkeySettings(ValkeyTopology.Standalone(endpoint), username = "ogcloud", password = "wrong"),
                    ) {
                        it.sync().ping()
                    }
                }
            }
    }

    @Test
    fun `discovers the primary through Sentinel`() {
        Network.newNetwork().use { network ->
            GenericContainer(
                VALKEY_IMAGE,
            ).withNetwork(network).withNetworkAliases(PRIMARY).withExposedPorts(VALKEY_PORT).use { primary ->
                primary.start()
                sentinel(network).use { sentinel ->
                    sentinel.start()
                    // Sentinel announces the primary by its network alias; map it to the port Docker exposes to the test.
                    val resources =
                        ClientResources
                            .builder()
                            .socketAddressResolver(
                                MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) { address ->
                                    if (address.hostText ==
                                        PRIMARY
                                    ) {
                                        LettuceHostAndPort.of(primary.host, primary.getMappedPort(VALKEY_PORT))
                                    } else {
                                        address
                                    }
                                },
                            ).build()
                    val settings =
                        ValkeySettings(
                            ValkeyTopology.Sentinel(
                                MASTER,
                                listOf(HostAndPort(sentinel.host, sentinel.getMappedPort(SENTINEL_PORT))),
                            ),
                        )

                    withConnection(settings, resources) { connection ->
                        connection.sync().set("probe", "ok".utf8())
                        connection.sync().get("probe")?.utf8() shouldBe "ok"
                    }
                    resources.shutdown()
                }
            }
        }
    }

    private fun sentinel(network: Network): GenericContainer<*> =
        GenericContainer(VALKEY_IMAGE)
            .withNetwork(network)
            .withExposedPorts(SENTINEL_PORT)
            .withCommand(
                "sh",
                "-c",
                "printf 'port $SENTINEL_PORT\\nsentinel resolve-hostnames yes\\nsentinel announce-hostnames yes\\n" +
                    "sentinel monitor $MASTER $PRIMARY $VALKEY_PORT 1\\n' > /tmp/sentinel.conf && valkey-server /tmp/sentinel.conf --sentinel",
            ).waitingFor(Wait.forLogMessage(".*\\+monitor master.*", 1))

    private fun withConnection(
        settings: ValkeySettings,
        resources: ClientResources = ClientResources.create(),
        block: (ValkeyConnection) -> Unit,
    ) {
        val (client, connection) = ValkeyConnections.connect(settings, resources)
        try {
            block(connection)
        } finally {
            connection.close()
            client.shutdown()
        }
    }

    private companion object {
        const val PRIMARY = "valkey-primary"
        const val MASTER = "ogcloud"
        const val SENTINEL_PORT = 26379
    }
}
