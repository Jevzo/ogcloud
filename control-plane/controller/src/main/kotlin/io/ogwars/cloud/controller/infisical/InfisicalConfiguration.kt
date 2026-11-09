package io.ogwars.cloud.controller.infisical

import io.ogwars.cloud.controller.jobs.ForwardingTokenSource
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.ssl.SslBundles
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.http.HttpClient

/** The forwarding token source, when Infisical is configured (D-0078). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("ogcloud.controller.infisical.url")
class InfisicalConfiguration {
    @Bean
    fun forwardingTokenSource(
        properties: InfisicalProperties,
        ssl: SslBundles,
    ): ForwardingTokenSource {
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(properties.timeout)
                .apply { properties.sslBundle?.let { sslContext(ssl.getBundle(it).createSslContext()) } }
                .build()
        val http =
            RestClient
                .builder()
                .baseUrl(properties.url.toString())
                .requestFactory(JdkClientHttpRequestFactory(client).apply { setReadTimeout(properties.timeout) })
                .build()
        return InfisicalForwardingTokenSource(InfisicalSecretClient(http, properties))
    }
}
