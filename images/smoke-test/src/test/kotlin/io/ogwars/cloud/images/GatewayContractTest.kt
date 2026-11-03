package io.ogwars.cloud.images

import io.ogwars.cloud.contracts.events.v1.NetworkSettingsUpdated
import io.ogwars.cloud.contracts.events.v1.NodeBroadcast
import io.ogwars.cloud.contracts.events.v1.NodeHeartbeat
import io.ogwars.cloud.contracts.events.v1.NodeType
import io.ogwars.cloud.contracts.events.v1.RequestServer
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.contracts.events.v1.ServerLifecycleChanged
import io.ogwars.cloud.contracts.node.v1.Delivery
import io.ogwars.cloud.contracts.node.v1.Heartbeat
import io.ogwars.cloud.contracts.node.v1.LiveChannelDelivery
import io.ogwars.cloud.contracts.node.v1.LiveChannelPublish
import io.ogwars.cloud.contracts.node.v1.ServerCommandRequest
import io.ogwars.cloud.contracts.node.v1.SessionRequest
import io.ogwars.cloud.contracts.node.v1.Welcome
import io.ogwars.cloud.controller.kube.PodLabels
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.livestate.HostAndPort
import io.ogwars.cloud.livestate.ValkeyConnections
import io.ogwars.cloud.livestate.ValkeyKeys
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.livestate.ValkeyServerStateStore
import io.ogwars.cloud.livestate.ValkeySettings
import io.ogwars.cloud.livestate.ValkeyTopology
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.node.client.DeliveryOutcome
import io.ogwars.cloud.node.client.NodeSettings
import io.ogwars.cloud.node.client.SessionListener
import io.ogwars.cloud.node.core.runtime.GatewayConnection
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.kafka.KafkaContainer
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The JVM node client against the real gateway (T-0510): the gateway image built with the contracttest tag, which
 * takes pods from a file instead of TokenReview, on a real Kafka and Valkey that the test seeds like the controller.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GatewayContractTest {
    private val network = Network.newNetwork()
    private val kafka =
        KafkaContainer(System.getProperty("ogcloud.image.kafka"))
            .withNetwork(network)
            .withListener("$KAFKA_ALIAS:$KAFKA_PORT")
            .also { it.start() }
    private val valkey =
        GenericContainer(System.getProperty("ogcloud.image.valkey"))
            .withNetwork(network)
            .withNetworkAliases(VALKEY_ALIAS)
            .withExposedPorts(VALKEY_PORT)
            .also { it.start() }
    private val server =
        Server(
            UUID.fromString("0192d2c1-0000-7000-8000-0000000000aa"),
            GroupId(GROUP),
            GroupType.DYNAMIC,
            PROFILE,
            ServerState.RUNNING,
            Instant.parse("2026-11-03T12:00:00Z"),
            maxPlayers = 20,
        )
    private val gateway = startGateway()
    private val files = Files.createTempDirectory("contract")
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val connection = GatewayConnection(settings(server.podName), scheduler)
    private val events = Events()
    private val session = connection.session(events).also { it.start() }
    private val welcome: Welcome = events.welcomes.poll(WAIT_SECONDS, TimeUnit.SECONDS) ?: error("no Welcome")

    @AfterAll
    fun stop() {
        session.close()
        connection.close()
        scheduler.shutdownNow()
        listOf(gateway, valkey, kafka).forEach(GenericContainer<*>::stop)
        network.close()
    }

    @Test
    fun `the Welcome carries the pod's identity and the network settings the controller published`() {
        welcome.serverId shouldBe server.id.toString()
        welcome.groupId shouldBe GROUP
        welcome.networkSettings.defaultGroupId shouldBe GROUP
    }

    @Test
    fun `heartbeats reach Kafka as the controller reads them`() {
        session.send(
            SessionRequest.newBuilder().setHeartbeat(Heartbeat.newBuilder().setPlayers(3).setMaxPlayers(20)).build(),
        )

        val heartbeat =
            consume(KafkaTopics.NODE_HEARTBEAT) { NodeHeartbeat.parseFrom(it).takeIf { h -> h.players == 3 } }
        heartbeat.nodeType shouldBe NodeType.NODE_TYPE_BACKEND
        heartbeat.serverId shouldBe welcome.serverId
    }

    @Test
    fun `a server request is answered with the new server's id, which the controller receives`() {
        val result =
            session
                .serverCommand(
                    ServerCommandRequest.newBuilder().setRequest(RequestServer.newBuilder().setGroupId(GROUP)),
                ).get(WAIT_SECONDS, TimeUnit.SECONDS)

        result.error shouldBe ""
        result.serverId shouldNotBe ""
        consume(KafkaTopics.SERVER_COMMAND) {
            ServerCommand.parseFrom(it).takeIf { c ->
                c.serverId == result.serverId
            }
        }.request.groupId shouldBe GROUP
    }

    @Test
    fun `broadcasts the controller publishes reach the session`() {
        val update =
            NetworkSettingsUpdated
                .newBuilder()
                .setVersion(2)
                .setDefaultGroupId(GROUP)
                .setMaintenance(true)
        // The gateway's consumer starts at the newest offset, so the broadcast repeats until it arrives.
        val received =
            repeatUntil(events.broadcasts) {
                publish(
                    KafkaTopics.NODE_BROADCAST,
                    NodeBroadcast
                        .newBuilder()
                        .setNetworkSettings(update)
                        .build()
                        .toByteArray(),
                )
            }
        received.networkSettings.maintenance shouldBe true
    }

    @Test
    fun `live channel messages reach every subscriber, the sender included`() {
        session.subscribe(CHANNEL)
        val payload = "hello".toByteArray()

        val received =
            repeatUntil(events.live) {
                session.send(
                    SessionRequest
                        .newBuilder()
                        .setLivePublish(
                            LiveChannelPublish
                                .newBuilder()
                                .setChannel(CHANNEL)
                                .setTypeId("contract")
                                .setPayload(
                                    com.google.protobuf.ByteString
                                        .copyFrom(payload),
                                ),
                        ).build(),
                )
            }
        received.senderServerId shouldBe welcome.serverId
        received.payload.toByteArray().decodeToString() shouldBe "hello"
    }

    @Test
    fun `queries read the servers the controller wrote to Valkey`() {
        val servers = connection.queries.servers(GROUP).get(WAIT_SECONDS, TimeUnit.SECONDS)

        servers.map { it.serverId } shouldContain welcome.serverId
        servers.single { it.serverId == welcome.serverId }.displayName shouldBe server.displayName
    }

    @Test
    fun `a token that names no pod is refused`() {
        val stranger = Events()
        GatewayConnection(settings("ghost"), scheduler).use { other ->
            other.session(stranger).use { session ->
                session.start()
                val cause = stranger.disconnects.poll(WAIT_SECONDS, TimeUnit.SECONDS)
                cause.toString() shouldContain "UNAUTHENTICATED"
                stranger.welcomes.isEmpty() shouldBe true
            }
        }
    }

    private fun startGateway(): GenericContainer<*> {
        seedValkey()
        createTopics()
        val labels =
            mapOf(
                PodLabels.MANAGED_BY to PodLabels.CONTROLLER_NAME,
                PodLabels.SERVER_ID to server.id.toString(),
                PodLabels.GROUP to GROUP,
                PodLabels.GROUP_TYPE to "dynamic",
                PodLabels.PROFILE to PROFILE,
                PodLabels.ROLE to "backend",
            )
        val pods = """[{"name": "${server.podName}", "uid": "contract-uid", "labels": ${json(labels)}}]"""
        return GenericContainer("${property("registry")}/gateway-contracttest:${property("version")}")
            .withNetwork(network)
            .withCopyToContainer(Transferable.of(pods), PODS_FILE)
            .withCopyToContainer(Transferable.of("contract"), "$SECRETS/s3-access")
            .withCopyToContainer(Transferable.of("contract"), "$SECRETS/s3-secret")
            .withEnv(
                mapOf(
                    "OGCLOUD_CONTRACT_PODS" to PODS_FILE,
                    "OGCLOUD_CONTRACT_CA_OUT" to CA_FILE,
                    "OGCLOUD_KAFKA_BROKERS" to "$KAFKA_ALIAS:$KAFKA_PORT",
                    "OGCLOUD_VALKEY_ADDRESSES" to "$VALKEY_ALIAS:$VALKEY_PORT",
                    // Neither is called in this test; the clients only connect on their first call.
                    "OGCLOUD_CONTROLLER_ADDRESS" to "controller.invalid:9090",
                    "OGCLOUD_S3_ENDPOINT" to "http://s3.invalid",
                    "OGCLOUD_S3_REGION" to "contract",
                    "OGCLOUD_S3_BUCKET" to "contract",
                    "OGCLOUD_S3_ACCESS_KEY_FILE" to "$SECRETS/s3-access",
                    "OGCLOUD_S3_SECRET_KEY_FILE" to "$SECRETS/s3-secret",
                ),
            ).withExposedPorts(GATEWAY_PORT)
            .waitingFor(Wait.forLogMessage(".*\"serving\".*", 1).withStartupTimeout(Duration.ofMinutes(1)))
            .also { it.start() }
    }

    private fun seedValkey() {
        val (client, connection) =
            ValkeyConnections.connect(
                ValkeySettings(ValkeyTopology.Standalone(HostAndPort(valkey.host, valkey.getMappedPort(VALKEY_PORT)))),
            )
        connection.use {
            ValkeyReadModelStore(it).put(
                ReadModelKind.NETWORK_SETTINGS,
                ValkeyKeys.NETWORK_SETTINGS_ID,
                NetworkSettingsUpdated
                    .newBuilder()
                    .setVersion(1)
                    .setDefaultGroupId(GROUP)
                    .build()
                    .toByteArray(),
            )
            ValkeyServerStateStore(it).create(server) shouldBe true
        }
        client.shutdown()
    }

    private fun createTopics() =
        AdminClient.create(mapOf("bootstrap.servers" to kafka.bootstrapServers)).use { admin ->
            val topics =
                listOf(
                    KafkaTopics.NODE_HEARTBEAT,
                    KafkaTopics.SERVER_COMMAND,
                    KafkaTopics.NODE_BROADCAST,
                    KafkaTopics.LIVE_CHANNEL,
                )
            admin.createTopics(topics.map { NewTopic(it, 1, 1) }).all().get(WAIT_SECONDS, TimeUnit.SECONDS)
        }

    private fun settings(token: String): NodeSettings {
        val ca = files.resolve("ca-$token.crt").also { gateway.copyFileFromContainer(CA_FILE, it.toString()) }
        val tokenFile = Files.writeString(files.resolve("token-$token"), token)
        return NodeSettings(
            "localhost:${gateway.getMappedPort(GATEWAY_PORT)}",
            tokenFile,
            ca,
            NodeType.NODE_TYPE_BACKEND,
            "contract-test",
        )
    }

    private fun publish(
        topic: String,
        value: ByteArray,
    ) = KafkaProducer(
        mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers),
        StringSerializer(),
        ByteArraySerializer(),
    ).use { it.send(ProducerRecord(topic, value)).get(WAIT_SECONDS, TimeUnit.SECONDS) }

    /** The first record on [topic] that [pick] accepts, reading from the start. */
    private fun <T> consume(
        topic: String,
        pick: (ByteArray) -> T?,
    ): T =
        KafkaConsumer(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "contract-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ),
            StringDeserializer(),
            ByteArrayDeserializer(),
        ).use { consumer ->
            consumer.subscribe(listOf(topic))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
            while (System.nanoTime() < deadline) {
                consumer
                    .poll(
                        Duration.ofMillis(POLL_MILLIS),
                    ).firstNotNullOfOrNull { pick(it.value()) }
                    ?.let { return it }
            }
            error("Nothing matching on $topic")
        }

    /** Repeats [action] until [queue] yields something; for paths whose consumers may join after the first try. */
    private fun <T> repeatUntil(
        queue: LinkedBlockingQueue<T>,
        action: () -> Unit,
    ): T {
        repeat(ATTEMPTS) {
            action()
            queue.poll(RETRY_SECONDS, TimeUnit.SECONDS)?.let { return it }
        }
        error("Nothing arrived after $ATTEMPTS attempts")
    }

    private fun json(values: Map<String, String>) =
        values.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\": \"$v\"" }

    /** What the session hands its listener, in arrival order. */
    private class Events : SessionListener {
        val welcomes = LinkedBlockingQueue<Welcome>()
        val broadcasts = LinkedBlockingQueue<NodeBroadcast>()
        val live = LinkedBlockingQueue<LiveChannelDelivery>()
        val disconnects = LinkedBlockingQueue<String>()

        override fun welcomed(welcome: Welcome) {
            welcomes += welcome
        }

        override fun lifecycle(event: ServerLifecycleChanged) = Unit

        override fun broadcast(event: NodeBroadcast) {
            broadcasts += event
        }

        override fun deliver(delivery: Delivery): CompletableFuture<DeliveryOutcome> =
            CompletableFuture.completedFuture(DeliveryOutcome(true))

        override fun liveMessage(message: LiveChannelDelivery) {
            live += message
        }

        override fun disconnected(cause: Throwable?) {
            disconnects += cause.toString()
        }
    }

    private companion object {
        const val GROUP = "lobby"
        val PROFILE = RuntimeProfiles.ALL.first { it.platform.name == "PAPER" }.id
        const val CHANNEL = "contract.test"
        const val KAFKA_ALIAS = "kafka"
        const val KAFKA_PORT = 19092
        const val VALKEY_ALIAS = "valkey"
        const val VALKEY_PORT = 6379
        const val GATEWAY_PORT = 9443
        const val PODS_FILE = "/contract/pods.json"
        const val CA_FILE = "/tmp/ca.crt"
        const val SECRETS = "/contract/secrets"
        const val WAIT_SECONDS = 30L
        const val RETRY_SECONDS = 2L
        const val ATTEMPTS = 15
        const val POLL_MILLIS = 500L
    }
}
