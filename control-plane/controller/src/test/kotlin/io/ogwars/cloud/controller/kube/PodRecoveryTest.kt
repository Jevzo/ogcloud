package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.T0
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class PodRecoveryTest {
    private val id = UUID.fromString("0192d2c1-1a2b-7c3d-8e4f-123456789abc")
    private val podName = "smp-0192d2c11a2b7c3d8e4f123456789abc"

    @Test
    fun `a pending pod becomes a PREPARING server`() {
        PodRecovery.serverFrom(pod(), T0) shouldBe
            Server(id, GroupId("smp"), GroupType.STATIC, "paper-26.3", ServerState.PREPARING, T0)
    }

    @Test
    fun `a running pod becomes a STARTING server at its address`() {
        val server = PodRecovery.serverFrom(pod(phase = "Running", ip = "10.42.0.3"), T0)

        server?.state shouldBe ServerState.STARTING
        server?.host shouldBe "10.42.0.3"
        server?.startedAt shouldBe T0
    }

    @Test
    fun `finished pods and incomplete or inconsistent labels give nothing`() {
        PodRecovery.serverFrom(pod(phase = "Failed"), T0) shouldBe null
        PodRecovery.serverFrom(pod(labels = labels() - PodLabels.PROFILE), T0) shouldBe null
        PodRecovery.serverFrom(pod(labels = labels() + (PodLabels.GROUP_TYPE to "unknown")), T0) shouldBe null
        PodRecovery.serverFrom(pod(labels = labels() + (PodLabels.GROUP to "Bad_Group")), T0) shouldBe null
        PodRecovery.serverFrom(pod(name = "other-name"), T0) shouldBe null
    }

    private fun labels() =
        mapOf(
            PodLabels.SERVER_ID to id.toString(),
            PodLabels.GROUP to "smp",
            PodLabels.GROUP_TYPE to "static",
            PodLabels.PROFILE to "paper-26.3",
        )

    private fun pod(
        phase: String = "Pending",
        ip: String? = null,
        labels: Map<String, String> = labels(),
        name: String = podName,
    ): Pod =
        PodBuilder()
            .withNewMetadata()
            .withName(name)
            .withLabels<String, String>(labels)
            .endMetadata()
            .withNewStatus()
            .withPhase(phase)
            .withPodIP(ip)
            .apply {
                if (ip != null) {
                    withContainerStatuses(
                        ContainerStatusBuilder()
                            .withName(PodSpecBuilder.SERVER_CONTAINER)
                            .withNewState()
                            .withNewRunning()
                            .endRunning()
                            .endState()
                            .build(),
                    )
                }
            }.endStatus()
            .build()
}
