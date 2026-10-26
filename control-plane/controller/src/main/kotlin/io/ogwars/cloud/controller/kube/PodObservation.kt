package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.lifecycle.PodEnd
import io.fabric8.kubernetes.api.model.Pod
import java.util.UUID

/** What a pod's status means for its server; pure, so every case is unit-tested. */
sealed interface PodObservation {
    val serverId: UUID

    data class Started(
        override val serverId: UUID,
        val host: String,
    ) : PodObservation

    data class Ended(
        override val serverId: UUID,
        val end: PodEnd,
        val reason: String,
    ) : PodObservation

    companion object {
        /** Null for pods that are not ours or have nothing new to report (still pending). */
        fun of(pod: Pod): PodObservation? {
            val serverId = serverId(pod) ?: return null
            val status = pod.status ?: return null
            val server = status.containerStatuses.orEmpty().find { it.name == PodSpecBuilder.SERVER_CONTAINER }
            val terminated = server?.state?.terminated
            return when {
                status.phase == PHASE_FAILED -> {
                    Ended(serverId, PodEnd.FAILED, status.reason ?: terminated?.reason ?: "pod failed")
                }

                status.phase == PHASE_SUCCEEDED -> {
                    Ended(serverId, PodEnd.STOPPED, "pod completed")
                }

                // With a native sidecar the pod outlives its server container, so the container's exit decides.
                terminated != null -> {
                    val end = if (terminated.exitCode == 0) PodEnd.STOPPED else PodEnd.FAILED
                    Ended(serverId, end, "server exited with ${terminated.exitCode}")
                }

                server?.state?.running != null && status.podIP != null -> {
                    Started(serverId, status.podIP)
                }

                else -> {
                    null
                }
            }
        }

        fun deleted(pod: Pod): PodObservation? = serverId(pod)?.let { Ended(it, PodEnd.STOPPED, "pod deleted") }

        fun serverId(pod: Pod): UUID? =
            pod.metadata?.labels?.get(PodLabels.SERVER_ID)?.let {
                runCatching { UUID.fromString(it) }.getOrNull()
            }

        private const val PHASE_FAILED = "Failed"
        private const val PHASE_SUCCEEDED = "Succeeded"
    }
}
