package io.ogwars.cloud.controller.consume

import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.readmodel.InMemoryNpcStore
import io.ogwars.cloud.controller.readmodel.InMemoryPermissionGroupStore
import io.ogwars.cloud.controller.readmodel.InMemoryPlayerStore
import io.ogwars.cloud.controller.readmodel.PlayerPermissions
import io.ogwars.cloud.controller.readmodel.RecordingBroadcasts
import io.ogwars.cloud.controller.readmodel.permissionGroup
import io.ogwars.cloud.controller.template.InMemoryTemplateVersionStore
import io.ogwars.cloud.controller.template.TemplateVersions
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KAFKA_IMAGE
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaSettings
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.kafka.KafkaContainer
import java.time.Duration
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** The consumers wired to a real broker: events in, live state out, bad commands dead-lettered. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ControllerConsumersTest {
    private val kafka = KafkaContainer(KAFKA_IMAGE).also { it.start() }
    private val components = KafkaComponents(KafkaSettings(kafka.bootstrapServers, "controller"))
    private val template = components.template()
    private val publisher = EventPublisher(template)
    private val bedwars = group(id = "bedwars")
    private val f = LifecycleFixture(listOf(group(id = "lobby"), bedwars))
    private val events = Events(f.clock)
    private val consumers =
        ControllerConsumers(
            components,
            template,
            ServerCommandHandler(f.lifecycle, f.groupStore, f.servers),
            HeartbeatHandler(f.lifecycle),
            GameStateHandler(f.lifecycle, f.network),
            PlayerActivityHandler(ValkeySessionStore(TestValkey.connection)),
            configHandler(),
            JobRequestedHandler {},
        ).also { it.start() }

    private fun configHandler(): ConfigChangedHandler {
        val readModels = ValkeyReadModelStore(TestValkey.connection)
        val sessions = ValkeySessionStore(TestValkey.connection)
        val permissionGroups = InMemoryPermissionGroupStore(permissionGroup("default", isDefault = true))
        val broadcasts = RecordingBroadcasts()
        val permissions = PlayerPermissions(InMemoryPlayerStore(), permissionGroups, sessions, readModels, broadcasts)
        return ConfigChangedHandler(
            f.groupStore,
            f.network,
            permissionGroups,
            InMemoryNpcStore(),
            readModels,
            broadcasts,
            permissions,
            sessions,
            TemplateVersions(
                f.servers,
                f.groupStore,
                InMemoryTemplateVersionStore(),
                readModels,
                { UUID.randomUUID() },
                f.clock,
            ),
        )
    }

    @AfterAll
    fun stop() {
        consumers.stop()
        template.destroy()
        kafka.stop()
    }

    @Test
    fun `a requested server starts running once its heartbeats arrive`() {
        val id = UUID.randomUUID()

        publisher.publishAndWait(KafkaTopics.SERVER_COMMAND, id.toString(), events.request(id, "bedwars"))
        runBlocking { eventually(TIMEOUT) { f.state(id) shouldBe ServerState.REQUESTED } }
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        publisher.publishAndWait(KafkaTopics.NODE_HEARTBEAT, id.toString(), events.heartbeat(id, players = 2))

        runBlocking { eventually(TIMEOUT) { f.state(id) shouldBe ServerState.RUNNING } }
    }

    @Test
    fun `a request for an unknown group is dead-lettered`() {
        val id = UUID.randomUUID()

        publisher.publishAndWait(KafkaTopics.SERVER_COMMAND, id.toString(), events.request(id, "ghost"))

        deadLetterKeys(KafkaTopics.SERVER_COMMAND_DLT).contains(id.toString()) shouldBe true
    }

    private fun deadLetterKeys(topic: String): Set<String> {
        val properties =
            mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "dlt-reader-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            )
        KafkaConsumer(properties, StringDeserializer(), ByteArrayDeserializer()).use { consumer ->
            consumer.subscribe(listOf(topic))
            val keys = mutableSetOf<String>()
            val deadline = System.nanoTime() + DLT_WAIT.toNanos()
            while (keys.isEmpty() && System.nanoTime() < deadline) consumer.poll(POLL).forEach { keys += it.key() }
            return keys
        }
    }

    private companion object {
        val TIMEOUT = 30.seconds
        val DLT_WAIT: Duration = Duration.ofSeconds(30)
        val POLL: Duration = Duration.ofMillis(500)
    }
}
