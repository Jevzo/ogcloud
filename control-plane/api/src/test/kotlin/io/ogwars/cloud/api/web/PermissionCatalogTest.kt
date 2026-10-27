package io.ogwars.cloud.api.web

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.service.GoldenFiles
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.nio.file.Path

/** Every API endpoint and the permission it needs (D-0092), reviewed as one file whenever it changes. */
@ApiTest
class PermissionCatalogTest(
    @Autowired @Qualifier("requestMappingHandlerMapping") private val mappings: RequestMappingHandlerMapping,
) {
    private val endpoints =
        mappings.handlerMethods
            .filter { (_, handler) ->
                handler.beanType.packageName.let {
                    it == API_PACKAGE ||
                        it.startsWith("$API_PACKAGE.")
                }
            }.flatMap { (info, handler) -> endpoints(info, handler) }
            .sortedBy { it.route }

    @Test
    fun `the permission of each endpoint matches the reviewed catalog`() {
        GoldenFiles.assertMatches(
            Path.of("src/test/resources/snapshots/api-permissions.txt"),
            endpoints.joinToString("\n", postfix = "\n") { "${it.route} ${it.access}" },
        )
    }

    @Test
    fun `no change is guarded by a read permission`() {
        endpoints.filter { it.method != "GET" && it.access.endsWith(":read") }.shouldBeEmpty()
    }

    private data class Endpoint(
        val method: String,
        val path: String,
        val access: String,
    ) {
        val route = "$method $path"
    }

    private fun endpoints(
        info: RequestMappingInfo,
        handler: HandlerMethod,
    ): List<Endpoint> {
        val access =
            handler.getMethodAnnotation(Requires::class.java)?.permission?.scope
                ?: when {
                    handler.hasMethodAnnotation(AnyCaller::class.java) -> "any-caller"
                    handler.hasMethodAnnotation(Anonymous::class.java) -> "anonymous"
                    else -> "undeclared"
                }
        val paths = info.pathPatternsCondition?.patternValues.orEmpty()
        val methods =
            info.methodsCondition.methods
                .map { it.name }
                .ifEmpty { listOf("ANY") }
        return paths.filter { it.startsWith("/api/v1/") }.flatMap { path -> methods.map { Endpoint(it, path, access) } }
    }

    private companion object {
        const val API_PACKAGE = "io.ogwars.cloud.api"
    }
}
