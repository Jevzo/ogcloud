package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.TestCluster
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.server
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.api.model.scheduling.v1.PriorityClassBuilder
import io.fabric8.kubernetes.client.KubernetesClient
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** The built pods pass a real API server's validation and the `restricted` Pod Security level (D-0056). */
class PodAdmissionTest {
    private val settings =
        PodProperties(
            imageRegistry = "ghcr.io/jevzo/ogcloud",
            platformVersion = "2.0.0",
            gatewayAddress = "ogcloud-gateway.ogcloud.svc:9443",
            namespace = NAMESPACE,
        )
    private val builder = PodSpecBuilder(settings)

    @Test
    fun `every pod variant is admitted under the restricted policy`() {
        TestCluster.client().use { client ->
            prepare(client)
            listOf(
                GroupType.DYNAMIC to "paper-26.3",
                GroupType.DYNAMIC to "pandaspigot-1.8.8",
                GroupType.STATIC to "paper-26.3",
                GroupType.PROXY to "velocity-4.3.0",
            ).forEach { (type, profileId) ->
                val group = group(type, id = "g-${type.name.lowercase()}", profileId = profileId)
                val server =
                    server(
                        state = ServerState.REQUESTED,
                        groupType = type,
                        group = group.id.value,
                        profileId = profileId,
                    )
                val pod =
                    builder.pod(
                        server,
                        group,
                        RuntimeProfile.find(profileId) ?: error(profileId),
                        GroupId("lobby"),
                    )

                val admitted =
                    client
                        .pods()
                        .inNamespace(NAMESPACE)
                        .resource(pod)
                        .dryRun()
                        .create()

                admitted.metadata.name shouldBe server.podName
            }
        }
    }

    private fun prepare(client: KubernetesClient) {
        client
            .namespaces()
            .resource(
                NamespaceBuilder()
                    .withNewMetadata()
                    .withName(NAMESPACE)
                    .addToLabels("pod-security.kubernetes.io/enforce", "restricted")
                    .endMetadata()
                    .build(),
            ).serverSideApply()
        client
            .serviceAccounts()
            .inNamespace(NAMESPACE)
            .resource(
                ServiceAccountBuilder()
                    .withNewMetadata()
                    .withName(settings.serviceAccount)
                    .endMetadata()
                    .build(),
            ).serverSideApply()
        with(settings.priorityClasses) { listOf(proxy, defaultGroup, game) }.forEachIndexed { index, name ->
            client
                .scheduling()
                .v1()
                .priorityClasses()
                .resource(
                    PriorityClassBuilder()
                        .withNewMetadata()
                        .withName(
                            name,
                        ).endMetadata()
                        .withValue(PRIORITY_BASE - index)
                        .build(),
                ).serverSideApply()
        }
    }

    private companion object {
        const val NAMESPACE = "pod-admission-test"
        const val PRIORITY_BASE = 1000
    }
}
