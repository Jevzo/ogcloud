package io.ogwars.cloud.api.web

import io.ogwars.cloud.domain.store.RateLimit
import io.ogwars.cloud.domain.store.RateLimiter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock

/** `ogcloud.rate-limits.*`, per client address (D-0086, D-0097). */
@ConfigurationProperties("ogcloud.rate-limits")
data class RateLimitProperties(
    /** Sign-in, second factor, token, setup and passkey endpoints. */
    val auth: RateLimit = RateLimit(capacity = 20, perMinute = 10),
    val api: RateLimit = RateLimit(capacity = 600, perMinute = 600),
)

/** Answers 429 once a client address used up its bucket; the address is the real one behind trusted proxies. */
class RateLimitFilter(
    private val limiter: RateLimiter,
    private val limits: RateLimitProperties,
    private val clock: Clock,
) : OncePerRequestFilter() {
    // Probes and scrapes come from inside the cluster and must never be throttled.
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.requestURI.startsWith(ACTUATOR)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val (bucket, limit) = if (SIGN_IN.matches(request)) AUTH_BUCKET to limits.auth else API_BUCKET to limits.api
        val decision = limiter.tryAcquire(bucket, request.remoteAddr, limit, clock.instant())
        if (decision.allowed) {
            chain.doFilter(request, response)
            return
        }
        val seconds = (decision.retryAfter.toMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", seconds.toString())
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.writer.write(
            """{"status":429,"title":"Too Many Requests","detail":"Try again in $seconds seconds."}""",
        )
    }

    companion object {
        const val AUTH_BUCKET = "auth"
        const val API_BUCKET = "api"
        private const val ACTUATOR = "/actuator"
        private const val MILLIS_PER_SECOND = 1000L
        private val paths = PathPatternRequestMatcher.withDefaults()

        val SIGN_IN: RequestMatcher =
            OrRequestMatcher(
                paths.matcher("/login"),
                paths.matcher("/login/**"),
                paths.matcher("/webauthn/**"),
                paths.matcher("/oauth2/authorize"),
                paths.matcher(HttpMethod.POST, "/oauth2/token"),
                paths.matcher(HttpMethod.POST, "/api/v1/setup"),
                paths.matcher("/api/v1/me/password"),
                paths.matcher("/api/v1/me/totp/**"),
                paths.matcher(HttpMethod.POST, "/api/v1/me/minecraft-link/**"),
            )
    }
}

@Configuration(proxyBeanMethods = false)
class RateLimitConfiguration {
    /**
     * Ahead of Spring Security, so brute force never reaches the password check. Filters are built while the web
     * server starts, so the limiter, and with it the Valkey connection, is looked up on first use.
     */
    @Bean
    fun rateLimitFilter(
        limiters: ObjectProvider<RateLimiter>,
        limits: RateLimitProperties,
        clock: Clock,
    ): FilterRegistrationBean<RateLimitFilter> {
        val limiter =
            RateLimiter {
                bucket,
                client,
                limit,
                at,
                ->
                limiters.getObject().tryAcquire(bucket, client, limit, at)
            }
        return FilterRegistrationBean(RateLimitFilter(limiter, limits, clock)).apply {
            order = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1
        }
    }
}
