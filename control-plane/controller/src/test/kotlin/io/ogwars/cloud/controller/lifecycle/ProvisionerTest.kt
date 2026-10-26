package io.ogwars.cloud.controller.lifecycle

import io.ogwars.cloud.controller.config.LifecycleProperties
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.server.ServerState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration

class ProvisionerTest {
    private val lobby = group(id = "lobby")
    private val smp = group(GroupType.STATIC, id = "smp")

    @Test
    fun `creates pods oldest first within the per-run budget`() {
        val f = fixture(LifecycleProperties(provisioning = LifecycleProperties.Provisioning(podsPerRun = 2)))
        val ids = List(3) { f.request(lobby).also { f.clock.advance(Duration.ofSeconds(1)) } }

        f.provisioner().runOnce()

        f.pods.created.map { it.metadata.labels["cloud.ogwars.io/server-id"] } shouldContainExactly
            ids.take(2).map(Any::toString)
        ids.map(f::state) shouldContainExactly
            listOf(ServerState.PREPARING, ServerState.PREPARING, ServerState.REQUESTED)
    }

    @Test
    fun `stops at the cap of preparing servers`() {
        val f =
            fixture(
                LifecycleProperties(provisioning = LifecycleProperties.Provisioning(podsPerRun = 5, maxPreparing = 1)),
            )
        f.request(lobby)
        f.request(lobby)

        f.provisioner().runOnce()
        f.provisioner().runOnce()

        f.pods.created.size shouldBe 1
    }

    @Test
    fun `waits for the EULA`() {
        val f = LifecycleFixture(listOf(lobby), eulaAccepted = false)
        f.request(lobby)

        f.provisioner().runOnce()

        f.pods.created.shouldBeEmpty()
    }

    @Test
    fun `holds back a group in crash-loop backoff`() {
        val f = fixture()
        val failed = f.request(lobby)
        f.lifecycle.podCreated(failed)
        f.lifecycle.podEnded(failed, PodEnd.FAILED, "crashed")
        val waiting = f.request(lobby)

        f.provisioner().runOnce()

        f.pods.created.shouldBeEmpty()
        f.state(waiting) shouldBe ServerState.REQUESTED
    }

    @Test
    fun `creates the volume claim for a STATIC server`() {
        val f = fixture()
        f.request(smp)

        f.provisioner().runOnce()

        f.pods.claims.map { it.metadata.name } shouldContainExactly listOf("static-smp")
    }

    @Test
    fun `stops servers of a deleted group and servers it cannot build`() {
        val f = fixture()
        val orphan = f.request(group(id = "gone"))
        f.removeGroup(group(id = "gone").id)
        val unknownProfile = f.request(lobby.copy(profileId = "paper-0.1"))

        f.provisioner().runOnce()

        f.state(orphan) shouldBe ServerState.STOPPING
        f.state(unknownProfile) shouldBe ServerState.STOPPING
    }

    private fun fixture(properties: LifecycleProperties = LifecycleProperties()) =
        LifecycleFixture(listOf(lobby, smp, group(id = "gone")), properties = properties)

    private fun LifecycleFixture.provisioner() =
        Provisioner(servers, groupStore, network, breaker, builder, pods, lifecycle, properties)
}
