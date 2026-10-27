package io.ogwars.cloud.api.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The committed contracts/openapi/openapi.yaml is rendered from this (D-0011); nothing in it may vary per run. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(Info().title("OgCloud API").version(API_VERSION))
            // Relative: the API is always served from the dashboard's own origin (D-0051).
            .servers(listOf(Server().url("/")))

    private companion object {
        const val API_VERSION = "v1"
    }
}
