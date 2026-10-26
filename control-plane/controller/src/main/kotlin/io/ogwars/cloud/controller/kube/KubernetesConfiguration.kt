package io.ogwars.cloud.controller.kube

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class KubernetesConfiguration {
    /** The pod's service account in the cluster, a kubeconfig elsewhere. */
    @Bean
    fun kubernetesConfig(): Config = Config.autoConfigure(null)

    @Bean(destroyMethod = "close")
    fun kubernetesClient(config: Config): KubernetesClient = KubernetesClientBuilder().withConfig(config).build()
}
