package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.jobs.ForwardingSecretReader
import io.fabric8.kubernetes.client.KubernetesClient
import org.springframework.stereotype.Component
import java.util.Base64

/** Reads the synced forwarding Secret that game pods mount; its `tokens` key holds one token per line. */
@Component
class FabricForwardingSecretReader(
    private val client: KubernetesClient,
    private val settings: PodProperties,
) : ForwardingSecretReader {
    override fun mountedTokens(): List<String> {
        val secret =
            client
                .secrets()
                .inNamespace(settings.namespace)
                .withName(settings.forwardingSecret)
                .get()
                ?: return emptyList()
        val encoded = secret.data?.get(TOKENS_KEY) ?: return emptyList()
        return String(Base64.getDecoder().decode(encoded)).lines().map(String::trim).filter(String::isNotEmpty)
    }

    companion object {
        /** The file name inside the mounted forwarding volume. */
        const val TOKENS_KEY = "tokens"
    }
}
