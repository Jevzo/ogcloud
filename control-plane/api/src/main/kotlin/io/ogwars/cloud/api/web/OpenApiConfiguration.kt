package io.ogwars.cloud.api.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.bind.annotation.RequestMapping

/** The committed contracts/openapi/openapi.yaml is rendered from this (D-0011); nothing in it may vary per run. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(Info().title("OgCloud API").version(API_VERSION))
            // Relative: the API is always served from the dashboard's own origin (D-0051).
            .servers(listOf(Server().url("/")))

    /** `<resource><Action>` ids such as `userList`, so the generated client gets unique, stable names (D-0096). */
    @Bean
    fun operationIds(): OperationCustomizer =
        OperationCustomizer { operation, handler ->
            val path =
                AnnotatedElementUtils
                    .findMergedAnnotation(
                        handler.beanType,
                        RequestMapping::class.java,
                    )?.path
                    ?.firstOrNull()
                    ?: error("${handler.beanType.simpleName} needs a class-level @RequestMapping path")
            operation.operationId(resourceName(path) + handler.method.name.replaceFirstChar(Char::uppercaseChar))
        }

    companion object {
        private const val API_VERSION = "v1"

        /** The singular, camel-case resource of a path: `/api/v1/oauth-clients` is `oauthClient`. */
        fun resourceName(path: String): String {
            val words = path.substringAfterLast('/').split('-')
            val camel = words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
            return if (camel.endsWith("s") && !camel.endsWith("ss")) camel.dropLast(1) else camel
        }
    }
}
