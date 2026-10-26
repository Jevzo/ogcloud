package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.TestCluster
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FabricPodGatewayTest {
    private val client = TestCluster.client()
    private val settings = PodProperties("ghcr.io/jevzo/ogcloud", "2.0.0", "gateway:9443", namespace = NAMESPACE)
    private val gateway = FabricPodGateway(client, settings)

    init {
        TestCluster.createServerNamespace(NAMESPACE)
    }

    @AfterAll
    fun close() {
        client.close()
    }

    @Test
    fun `creating a pod twice is a no-op and annotations can be patched`() {
        val pod = TestCluster.serverPod(NAMESPACE, "lobby-create", UUID.randomUUID())

        gateway.create(pod)
        gateway.create(pod)
        gateway.setSafeToEvict("lobby-create", false)

        client
            .pods()
            .inNamespace(
                NAMESPACE,
            ).withName("lobby-create")
            .get()
            .metadata.annotations[PodLabels.SAFE_TO_EVICT] shouldBe
            "false"
    }

    @Test
    fun `deleting or annotating a missing pod is fine`() {
        gateway.delete("lobby-missing")
        gateway.setSafeToEvict("lobby-missing", true)
    }

    @Test
    fun `the static volume claim can be applied repeatedly`() {
        val claim = PodSpecBuilder(settings).staticVolumeClaim(group(GroupType.STATIC, id = "smp"))

        gateway.ensureVolumeClaim(claim)
        gateway.ensureVolumeClaim(claim)

        client
            .persistentVolumeClaims()
            .inNamespace(
                NAMESPACE,
            ).withName("static-smp")
            .get()
            .spec.resources.requests["storage"]
            ?.toString() shouldBe
            "10Gi"
    }

    private companion object {
        const val NAMESPACE = "gateway-test"
    }
}
