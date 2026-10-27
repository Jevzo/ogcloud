package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.domain.user.MfaRequirement
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Adding a factor needs a login that already meets the MFA policy, or an owner who has none yet (D-0097);
 * otherwise a stolen password could enroll its own second factor. A filter, because Spring's passkey
 * registration filters answer before URL authorization runs.
 */
class EnrollmentGate(
    private val enrollment: RequestMatcher,
    private val requirements: MfaRequirements,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !enrollment.matches(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val login = SecurityContextHolder.getContext().authentication
        if (login != null && login.isAuthenticated && login !is AnonymousAuthenticationToken &&
            requirements.of(login) !in ALLOWED
        ) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN)
            return
        }
        chain.doFilter(request, response)
    }

    private companion object {
        val ALLOWED = setOf(MfaRequirement.NONE, MfaRequirement.ENROLLMENT)
    }
}
