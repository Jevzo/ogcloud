package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.lifecycle.PodEnd
import io.fabric8.kubernetes.api.model.ContainerStateBuilder
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class PodObservationTest {
    private val id = UUID.fromString("0192d2c1-1a2b-7c3d-8e4f-123456789abc")

    @Test
    fun `a pending pod reports nothing`() {
        PodObservation.of(pod("Pending")) shouldBe null
    }

    @Test
    fun `a running server container with an address has started`() {
        PodObservation.of(pod("Running", running = true, ip = "10.42.0.9")) shouldBe
            PodObservation.Started(id, "10.42.0.9")
    }

    @Test
    fun `a failed pod ends with a failure`() {
        PodObservation.of(pod("Failed")) shouldBe PodObservation.Ended(id, PodEnd.FAILED, "pod failed")
    }

    @Test
    fun `a completed pod ends normally`() {
        PodObservation.of(pod("Succeeded")) shouldBe PodObservation.Ended(id, PodEnd.STOPPED, "pod completed")
    }

    @Test
    fun `the server container's exit decides while the sidecar still runs`() {
        PodObservation.of(pod("Running", exitCode = 1)) shouldBe
            PodObservation.Ended(id, PodEnd.FAILED, "server exited with 1")
        PodObservation.of(pod("Running", exitCode = 0)) shouldBe
            PodObservation.Ended(id, PodEnd.STOPPED, "server exited with 0")
    }

    @Test
    fun `pods without a valid server id are ignored`() {
        PodObservation.of(pod("Failed").apply { metadata.labels[PodLabels.SERVER_ID] = "not-a-uuid" }) shouldBe null
    }

    private fun pod(
        phase: String,
        running: Boolean = false,
        ip: String? = null,
        exitCode: Int? = null,
    ): Pod {
        val state =
            ContainerStateBuilder()
                .apply {
                    if (running) withNewRunning().endRunning()
                    if (exitCode != null) withNewTerminated().withExitCode(exitCode).endTerminated()
                }.build()
        return PodBuilder()
            .withNewMetadata()
            .withName("lobby-pod")
            .withLabels<String, String>(mutableMapOf(PodLabels.SERVER_ID to id.toString()))
            .endMetadata()
            .withNewStatus()
            .withPhase(phase)
            .withPodIP(ip)
            .withContainerStatuses(
                ContainerStatusBuilder().withName(PodSpecBuilder.SERVER_CONTAINER).withState(state).build(),
            ).endStatus()
            .build()
    }
}
