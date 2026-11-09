package io.ogwars.cloud.livestate

import io.ogwars.cloud.service.TestTls
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
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
    fun `discovers the primary through a TLS Sentinel that requires the client's user`() {
        Network.newNetwork().use { network ->
            primary(network).use { primary ->
                primary.start()
                sentinel(network).use { sentinel ->
                    sentinel.start()
                    // Sentinel announces the primary by its network alias; map it to the port Docker exposes to the test.
                    val resources =
                        ClientResources
                            .builder()
                            .socketAddressResolver(
                                MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) { address ->
                                    if (address.hostText == PRIMARY) {
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
                            username = USER,
                            password = PASSWORD,
                            tls = ValkeyTls(TestTls.certificate.toString()),
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

    /** TLS only, with the default user off, as the chart runs it (D-0041, D-0048). */
    private fun primary(network: Network): GenericContainer<*> =
        tlsContainer(network)
            .withNetworkAliases(PRIMARY)
            .withExposedPorts(VALKEY_PORT)
            .withCommand(
                "valkey-server",
                *TLS_ARGS,
                "--tls-port",
                "$VALKEY_PORT",
                "--user",
                "default",
                "off",
                "--user",
                USER,
                "on",
                ">$PASSWORD",
                "~*",
                "&*",
                "+@all",
            )

    /** Clients log in to it with their own user, which may only discover the primary. */
    private fun sentinel(network: Network): GenericContainer<*> =
        tlsContainer(network)
            .withExposedPorts(SENTINEL_PORT)
            .withCommand(
                "sh",
                "-c",
                "printf '" +
                    "sentinel resolve-hostnames yes\\nsentinel announce-hostnames yes\\n" +
                    "sentinel monitor $MASTER $PRIMARY $VALKEY_PORT 1\\n" +
                    "sentinel auth-user $MASTER $USER\\nsentinel auth-pass $MASTER $PASSWORD\\n" +
                    "user default off\\nuser $USER on >$PASSWORD allchannels -@all +@connection -@admin -@dangerous " +
                    "+sentinel|get-master-addr-by-name\\n' > /tmp/sentinel.conf && " +
                    "valkey-server /tmp/sentinel.conf --sentinel ${TLS_ARGS.joinToString(
                        " ",
                    )} --tls-port $SENTINEL_PORT",
            ).waitingFor(Wait.forLogMessage(".*\\+monitor master.*", 1))

    private fun tlsContainer(network: Network): GenericContainer<*> =
        GenericContainer(VALKEY_IMAGE)
            .withNetwork(network)
            .withCopyToContainer(MountableFile.forHostPath(TestTls.certificate, READABLE), "/tls/tls.crt")
            .withCopyToContainer(MountableFile.forHostPath(TestTls.privateKey, READABLE), "/tls/tls.key")

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
        const val USER = "ogcloud"
        const val PASSWORD = "s3cret"
        const val READABLE = 0b110_100_100
        val TLS_ARGS =
            arrayOf(
                "--port",
                "0",
                "--tls-cert-file",
                "/tls/tls.crt",
                "--tls-key-file",
                "/tls/tls.key",
                "--tls-ca-cert-file",
                "/tls/tls.crt",
                "--tls-auth-clients",
                "no",
                "--tls-replication",
                "yes",
            )
    }
}
