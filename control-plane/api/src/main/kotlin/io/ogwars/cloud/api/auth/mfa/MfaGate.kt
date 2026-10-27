package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.domain.user.MfaRequirement
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Holds an authorization request back until the login meets the MFA policy (D-0097), so no app gets a code
 * before that. The request is saved and resumed once the second factor is done.
 */
class MfaGate(
    private val authorizationEndpoint: RequestMatcher,
    private val requirements: MfaRequirements,
) : OncePerRequestFilter() {
    private val requestCache = HttpSessionRequestCache()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !authorizationEndpoint.matches(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val login = SecurityContextHolder.getContext().authentication
        if (login == null || !login.isAuthenticated || login is AnonymousAuthenticationToken ||
            requirements.of(login) == MfaRequirement.NONE
        ) {
            chain.doFilter(request, response)
            return
        }
        requestCache.saveRequest(request, response)
        response.sendRedirect(request.contextPath + MfaPage.PATH)
    }
}
