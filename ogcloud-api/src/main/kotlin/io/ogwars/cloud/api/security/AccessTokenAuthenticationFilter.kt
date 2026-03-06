package io.ogwars.cloud.api.security

import io.ogwars.cloud.api.exception.InvalidCredentialsException
import io.ogwars.cloud.api.model.WebUserRole
import io.ogwars.cloud.api.repository.WebUserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AccessTokenAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val webUserRepository: WebUserRepository
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader(AUTHORIZATION_HEADER)
        if (header.isNullOrBlank() || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response)
            return
        }

        val rawToken = header.removePrefix(BEARER_PREFIX).trim()
        if (rawToken.isBlank()) {
            writeUnauthorized(response)
            return
        }

        try {
            val jwt = jwtTokenService.parseAccessToken(rawToken)
            val userId = jwt.subject ?: throw InvalidCredentialsException()
            val user = webUserRepository.findById(userId).orElseThrow { InvalidCredentialsException() }
            val principal = AuthenticatedUser(user.id, user.email, user.role)
            val authorities = buildAuthorities(user.role)

            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
            )
        } catch (_: InvalidCredentialsException) {
            writeUnauthorized(response)
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = "application/json"
        response.writer.write("""{"status":401,"message":"Unauthorized","details":[]}""")
    }

    private fun buildAuthorities(role: WebUserRole): List<GrantedAuthority> {
        val authorities = mutableListOf<GrantedAuthority>(
            SimpleGrantedAuthority("ROLE_${role.name}")
        )

        if (role == WebUserRole.SERVICE) {
            // SERVICE is admin-equivalent and must pass ADMIN role checks.
            authorities += SimpleGrantedAuthority("ROLE_${WebUserRole.ADMIN.name}")
        }

        return authorities
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
    }
}
