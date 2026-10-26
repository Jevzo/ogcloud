package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.server.Server
import io.ogwars.cloud.domain.server.ServerState
import io.fabric8.kubernetes.api.model.Pod
import java.time.Instant

/** Rebuilds a server from its pod's labels when live state lost the record, e.g. in a Valkey failover (D-0048). */
object PodRecovery {
    /** Null when the labels are incomplete or the pod already finished. Deadlines restart at [now]. */
    fun serverFrom(
        pod: Pod,
        now: Instant,
    ): Server? {
        val labels = pod.metadata?.labels ?: return null
        val id = PodObservation.serverId(pod) ?: return null
        val groupId = labels[PodLabels.GROUP]?.let { runCatching { GroupId(it) }.getOrNull() } ?: return null
        val type =
            labels[PodLabels.GROUP_TYPE]?.let { label -> GroupType.entries.find { it.name.lowercase() == label } }
                ?: return null
        val profileId = labels[PodLabels.PROFILE] ?: return null
        val server =
            when (val observation = PodObservation.of(pod)) {
                null -> {
                    Server(id, groupId, type, profileId, ServerState.PREPARING, now)
                }

                // The first heartbeat moves it to RUNNING again and restores the player counts.
                is PodObservation.Started -> {
                    Server(id, groupId, type, profileId, ServerState.STARTING, now, startedAt = now)
                        .copy(host = observation.host, port = PodSpecBuilder.MINECRAFT_PORT)
                }

                is PodObservation.Ended -> {
                    return null
                }
            }
        return server.takeIf { it.podName == pod.metadata.name }
    }
}
