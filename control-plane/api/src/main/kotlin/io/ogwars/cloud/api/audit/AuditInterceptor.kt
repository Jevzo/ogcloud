package io.ogwars.cloud.api.audit

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.store.AuditLogStore
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.time.Clock

/** For POSTs that change nothing, e.g. issuing a live-update ticket. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotAudited

/** Who acts on the API; resolved from the access token. */
fun interface CurrentActor {
    fun current(): Actor
}

/** Every successful change through the API lands in the audit log (D-0049 keeps it pseudonymisable). */
@Component
class AuditInterceptor(
    private val audit: AuditLogStore,
    private val actors: CurrentActor,
    private val ids: IdGenerator,
    private val clock: Clock,
) : HandlerInterceptor {
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        if (request.method !in CHANGING_METHODS || ex != null || response.status >= FIRST_ERROR_STATUS) return
        if (handler is HandlerMethod && handler.hasMethodAnnotation(NotAudited::class.java)) return
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String ?: return

        @Suppress("UNCHECKED_CAST") // Spring stores the URI template variables as a String map.
        val variables =
            request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<String, String> ?: emptyMap()
        val entry =
            AuditEntry(
                id = ids.next(),
                occurredAt = clock.instant(),
                actor = actors.current(),
                action = "${request.method} $pattern",
                targetType = pattern.removePrefix(API_PREFIX).trimStart('/').substringBefore('/'),
                targetId = variables.values.firstOrNull().orEmpty(),
            )
        audit.append(entry)
    }

    private companion object {
        val CHANGING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        const val FIRST_ERROR_STATUS = 400
        const val API_PREFIX = "/api/v1"
    }
}
