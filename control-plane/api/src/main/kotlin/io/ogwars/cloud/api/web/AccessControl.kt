package io.ogwars.cloud.api.web

import io.ogwars.cloud.domain.user.ApiPermission
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** The permission an API handler needs (D-0092). */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Requires(
    val permission: ApiPermission,
)

/** Any caller with a valid access token, e.g. for their own profile. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AnyCaller

/** No access token; the security chain must permit the path as well. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Anonymous

/** The permissions the caller's access token grants (D-0092). */
fun callerPermissions(): Set<String> =
    SecurityContextHolder
        .getContext()
        .authentication
        ?.authorities
        .orEmpty()
        .mapNotNull { it.authority }
        .toSet()

/** Fails closed: an API handler that declares none of [Requires], [AnyCaller] or [Anonymous] is refused. */
@Component
class PermissionInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) return true
        val required = handler.getMethodAnnotation(Requires::class.java)
        if (required != null) {
            if (required.permission.scope !in
                callerPermissions()
            ) {
                throw AccessDeniedException("Requires ${required.permission.scope}")
            }
        } else if (!handler.hasMethodAnnotation(AnyCaller::class.java) &&
            !handler.hasMethodAnnotation(Anonymous::class.java)
        ) {
            throw AccessDeniedException("${handler.shortLogMessage} declares no permission")
        }
        return true
    }
}

@Configuration(proxyBeanMethods = false)
class AccessConfiguration(
    private val permissions: PermissionInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(permissions).addPathPatterns("/api/v1/**")
    }
}
