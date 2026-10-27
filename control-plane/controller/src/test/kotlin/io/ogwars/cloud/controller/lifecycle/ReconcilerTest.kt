package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.TestCluster
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.kube.FabricPodGateway
import io.ogwars.cloud.controller.kube.PodLabels
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.livestate.ValkeySessionStore
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** Drift is injected directly into Kubernetes and Valkey, then one reconciler run must repair it. */
class ReconcilerTest {
    private val lobby = group(id = "lobby")
    private val proxy = group(GroupType.PROXY, id = "proxy")
    private val f = LifecycleFixture(listOf(lobby, proxy))
    private val client = TestCluster.client()
    private val gateway =
        FabricPodGateway(client, PodProperties("ghcr.io/jevzo/ogcloud", "2.0.0", "gateway:9443", namespace = NAMESPACE))
    private val sessions = ValkeySessionStore(TestValkey.connection)
    private val reconciler =
        Reconciler(
            f.servers,
            sessions,
            f.groupStore,
            gateway,
            f.lifecycle,
            f.properties,
            f.clock,
            SimpleMeterRegistry(),
        )

    init {
        TestCluster.createServerNamespace(NAMESPACE)
        client.pods().inNamespace(NAMESPACE).delete()
    }

    @AfterEach
    fun close() {
        client.close()
    }

    @Test
    fun `an orphan pod of a known group is restored from its labels`() {
        val id = UUID.randomUUID()
        createPod(lobby, id)

        reconciler.runOnce()

        f.state(id) shouldBe ServerState.PREPARING
        f.events.transitions
            .last()
            .third shouldBe "restored from its pod"
    }

    @Test
    fun `pods that match no group or carry no valid id are deleted`() {
        val ghost = createPod(group(id = "ghost"), UUID.randomUUID())
        val unlabelled =
            client
                .pods()
                .inNamespace(NAMESPACE)
                .resource(
                    TestCluster.serverPod(NAMESPACE, "lobby-unlabelled", UUID.randomUUID()).apply {
                        metadata.labels[PodLabels.SERVER_ID] =
                            "nope"
                    },
                ).create()
                .metadata.name

        reconciler.runOnce()

        runBlocking { eventually(TIMEOUT) { listOf(ghost, unlabelled).mapNotNull(::livePod) shouldBe emptyList() } }
    }

    @Test
    fun `a server whose pod vanished is ended, a requested one is left alone`() {
        val vanished = running(lobby)
        val requested = f.request(lobby)

        reconciler.runOnce()

        f.state(vanished) shouldBe null
        f.state(requested) shouldBe ServerState.REQUESTED
    }

    @Test
    fun `a terminating pod keeps its server until the pod is gone`() {
        val id = running(lobby)
        val name = createPod(lobby, id, finalizer = true)
        f.lifecycle.stopNow(id, ACTOR, "stop")
        client
            .pods()
            .inNamespace(NAMESPACE)
            .withName(name)
            .delete()
        val session = session(id)

        try {
            reconciler.runOnce()

            f.state(id) shouldBe ServerState.STOPPING
            sessions.get(session)?.value?.proxyId shouldBe id
        } finally {
            client
                .pods()
                .inNamespace(NAMESPACE)
                .withName(name)
                .edit { it.apply { metadata.finalizers = emptyList() } }
        }
    }

    @Test
    fun `sessions on a vanished proxy are dropped`() {
        val liveProxy = running(proxy).also { createPod(proxy, it) }
        val onLiveProxy = session(liveProxy)
        val onGoneProxy = session(UUID.randomUUID())

        reconciler.runOnce()

        sessions.onlinePlayers() shouldBe setOf(onLiveProxy)
        sessions.get(onGoneProxy) shouldBe null
    }

    @Test
    fun `a run without drift changes nothing`() {
        val id = running(lobby).also { createPod(lobby, it) }
        val before = f.events.transitions.toList()

        reconciler.runOnce()

        f.state(id) shouldBe ServerState.RUNNING
        f.events.transitions shouldContainExactly before
    }

    private fun running(group: Group): UUID {
        val id = f.request(group)
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        f.lifecycle.heartbeat(id, players = 0, maxPlayers = 50, at = f.clock.instant())
        return id
    }

    /** With [finalizer], a deleted pod stays visible as terminating until the test releases it. */
    private fun createPod(
        group: Group,
        id: UUID,
        finalizer: Boolean = false,
    ): String {
        val name = "${group.id}-${id.toString().replace("-", "")}"
        val labels =
            mapOf(
                PodLabels.GROUP to group.id.value,
                PodLabels.GROUP_TYPE to group.type.name.lowercase(),
                PodLabels.PROFILE to group.profileId,
            )
        val pod =
            TestCluster.serverPod(NAMESPACE, name, id, labels).apply {
                if (finalizer) {
                    metadata.finalizers =
                        listOf(HOLD_FINALIZER)
                }
            }
        return client
            .pods()
            .inNamespace(NAMESPACE)
            .resource(pod)
            .create()
            .metadata.name
    }

    private fun session(proxyId: UUID): UUID {
        val player = UUID.randomUUID()
        sessions.put(
            PlayerSession(player, "p${player.toString().take(8)}", proxyId, null, "default", 0, f.clock.instant()),
        )
        return player
    }

    private fun livePod(name: String) =
        client
            .pods()
            .inNamespace(NAMESPACE)
            .withName(name)
            .get()

    private companion object {
        const val NAMESPACE = "reconcile-test"
        const val HOLD_FINALIZER = "test.ogwars.io/hold"
        val ACTOR = Actor.system("test")
        val TIMEOUT = 30.seconds
    }
}
