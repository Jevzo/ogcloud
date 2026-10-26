package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.TestCluster
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.fabric8.kubernetes.api.model.Pod
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class PodWatcherTest {
    private val lobby = group(id = "lobby")
    private val f = LifecycleFixture(listOf(lobby))
    private val client = TestCluster.client()
    private val settings = PodProperties("ghcr.io/jevzo/ogcloud", "2.0.0", "gateway:9443", namespace = NAMESPACE)
    private val watcher = PodWatcher(client, settings, f.lifecycle, FabricPodGateway(client, settings))

    init {
        TestCluster.createServerNamespace(NAMESPACE)
    }

    @AfterEach
    fun stop() {
        watcher.stopLeading()
        client
            .nodes()
            .list()
            .items
            .forEach { node -> TestCluster.setUnschedulable(client, node.metadata.name, false) }
        client.close()
    }

    @Test
    fun `a deleted pod stops its server`() {
        val (id, pod) = runningServerWithPod(players = 0)
        watcher.startLeading()

        client
            .pods()
            .inNamespace(NAMESPACE)
            .withName(pod.metadata.name)
            .delete()

        runBlocking { eventually(TIMEOUT) { f.state(id) shouldBe null } }
        f.events.transitions
            .last()
            .second shouldBe ServerState.STOPPED
    }

    @Test
    fun `servers on a cordoned node drain`() {
        val (id, pod) = runningServerWithPod(players = 5)
        val node = runBlocking { eventually(TIMEOUT) { scheduledNode(pod).shouldNotBeNull() } }
        watcher.startLeading()

        TestCluster.setUnschedulable(client, node, true)

        runBlocking { eventually(TIMEOUT) { f.state(id) shouldBe ServerState.DRAINING } }
    }

    private fun runningServerWithPod(players: Int): Pair<UUID, Pod> {
        val id = f.request(lobby)
        f.lifecycle.podCreated(id)
        f.lifecycle.podStarted(id, "10.42.0.7")
        f.lifecycle.heartbeat(id, players, maxPlayers = 50, at = f.clock.instant())
        val name =
            f.servers
                .get(id)
                ?.value
                ?.podName
                .shouldNotBeNull()
        val pod =
            client
                .pods()
                .inNamespace(NAMESPACE)
                .resource(TestCluster.serverPod(NAMESPACE, name, id))
                .create()
        return id to pod
    }

    private fun scheduledNode(pod: Pod): String? =
        client
            .pods()
            .inNamespace(NAMESPACE)
            .withName(pod.metadata.name)
            .get()
            ?.spec
            ?.nodeName

    private companion object {
        const val NAMESPACE = "watcher-test"
        val TIMEOUT = 30.seconds
    }
}
