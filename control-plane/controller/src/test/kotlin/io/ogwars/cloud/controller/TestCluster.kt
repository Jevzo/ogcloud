package io.ogwars.cloud.controller

import io.ogwars.cloud.controller.kube.PodLabels
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/** The k3s line kube-hetzner installs by default (D-0070). */
const val K3S_IMAGE = "rancher/k3s:v1.36.5-k3s1"

/** One k3s cluster per test JVM; starting it is the slow part, so test classes share it. */
object TestCluster {
    private val k3s: K3sContainer by lazy { K3sContainer(DockerImageName.parse(K3S_IMAGE)).also { it.start() } }

    val config: Config by lazy { Config.fromKubeconfig(k3s.kubeConfigYaml) }

    fun client(): KubernetesClient = KubernetesClientBuilder().withConfig(config).build()

    fun createNamespace(name: String) {
        client().use { client ->
            client
                .namespaces()
                .resource(
                    NamespaceBuilder()
                        .withNewMetadata()
                        .withName(name)
                        .endMetadata()
                        .build(),
                ).serverSideApply()
        }
    }

    /** A namespace where bare pods of a server can be created: it needs the service account they reference. */
    fun createServerNamespace(name: String) {
        createNamespace(name)
        client().use { client ->
            client
                .serviceAccounts()
                .inNamespace(name)
                .resource(
                    ServiceAccountBuilder()
                        .withNewMetadata()
                        .withName(SERVER_ACCOUNT)
                        .endMetadata()
                        .build(),
                ).serverSideApply()
        }
    }

    /** A pod labelled like a server's; its image never pulls, so it stays pending but gets scheduled. */
    fun serverPod(
        namespace: String,
        name: String,
        serverId: UUID,
        extraLabels: Map<String, String> = emptyMap(),
    ): Pod =
        PodBuilder()
            .withNewMetadata()
            .withName(name)
            .withNamespace(namespace)
            .withLabels<String, String>(
                mapOf(PodLabels.MANAGED_BY to PodLabels.CONTROLLER_NAME, PodLabels.SERVER_ID to serverId.toString()) +
                    extraLabels,
            ).endMetadata()
            .withNewSpec()
            .withServiceAccountName(SERVER_ACCOUNT)
            .withRestartPolicy("Never")
            .addNewContainer()
            .withName(PodSpecBuilder.SERVER_CONTAINER)
            .withImage("registry.invalid/ogcloud/never:0")
            .endContainer()
            .endSpec()
            .build()

    fun setUnschedulable(
        client: KubernetesClient,
        node: String,
        unschedulable: Boolean,
    ) {
        client.nodes().withName(node).edit { it.apply { spec.unschedulable = unschedulable } }
    }

    private const val SERVER_ACCOUNT = "ogcloud-server"
}
