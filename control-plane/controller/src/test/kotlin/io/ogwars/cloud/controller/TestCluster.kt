package io.ogwars.cloud.controller

import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.utility.DockerImageName

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
}
