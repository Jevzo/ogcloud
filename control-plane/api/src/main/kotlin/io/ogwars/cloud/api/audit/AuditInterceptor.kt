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

/** Changes the interceptor must not record: nothing changed (a live-update ticket), or the service records it. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotAudited

/** Who acts on the API; resolved from the access token. */
fun interface CurrentActor {
    fun current(): Actor
}

/** Appends audit entries; the interceptor records plain changes, services the entries it cannot (D-0107). */
@Component
class AuditTrail(
    private val audit: AuditLogStore,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun record(
        actor: Actor,
        action: String,
        targetType: String,
        targetId: String,
    ) = audit.append(AuditEntry(ids.next(), clock.instant(), actor, action, targetType, targetId))
}

/** Every successful change through the API lands in the audit log (D-0049 keeps it pseudonymisable). */
@Component
class AuditInterceptor(
    private val trail: AuditTrail,
    private val actors: CurrentActor,
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
        trail.record(
            actors.current(),
            "${request.method} $pattern",
            pattern.removePrefix(API_PREFIX).trimStart('/').substringBefore('/'),
            variables.values.firstOrNull().orEmpty(),
        )
    }

    private companion object {
        val CHANGING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
        const val FIRST_ERROR_STATUS = 400
        const val API_PREFIX = "/api/v1"
    }
}
