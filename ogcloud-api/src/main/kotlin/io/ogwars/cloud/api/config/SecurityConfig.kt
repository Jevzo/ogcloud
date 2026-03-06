package io.ogwars.cloud.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetSequenceKey
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import io.ogwars.cloud.api.dto.ErrorResponse
import io.ogwars.cloud.api.security.AccessTokenAuthenticationFilter
import io.ogwars.cloud.api.security.LengthSafeBcryptPasswordEncoder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.nio.charset.StandardCharsets
import java.time.Clock
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties::class, CorsProperties::class)
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        accessTokenAuthenticationFilter: AccessTokenAuthenticationFilter,
        objectMapper: ObjectMapper
    ): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/health").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                it.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeError(objectMapper, response, 401, "Unauthorized")
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeError(objectMapper, response, 403, "Forbidden")
                }
            }
            .addFilterBefore(accessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = LengthSafeBcryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(corsProperties: CorsProperties): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = corsProperties.allowedOrigins
            allowedMethods = ALLOWED_METHODS
            allowedHeaders = ALLOWED_HEADERS
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(ALL_PATHS, configuration)
        }
    }

    @Bean
    fun jwtSecretKey(authProperties: AuthProperties): SecretKey {
        val secretBytes = authProperties.jwtSecret.toByteArray(StandardCharsets.UTF_8)

        require(secretBytes.size >= MIN_SECRET_BYTES) {
            "OGCLOUD_AUTH_JWT_SECRET must be at least $MIN_SECRET_BYTES bytes"
        }

        return SecretKeySpec(secretBytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(secretKey: SecretKey): JwtEncoder {
        val jwk = OctetSequenceKey.Builder(secretKey)
            .algorithm(JWSAlgorithm.HS256)
            .build()

        val jwkSource = JWKSource<SecurityContext> { selector, _ ->
            selector.select(JWKSet(jwk))
        }

        return NimbusJwtEncoder(jwkSource)
    }

    @Bean
    fun jwtDecoder(secretKey: SecretKey): JwtDecoder {
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
    }

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    private fun writeError(
        objectMapper: ObjectMapper,
        response: jakarta.servlet.http.HttpServletResponse,
        status: Int,
        message: String
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        response.writer.write(objectMapper.writeValueAsString(ErrorResponse(status = status, message = message)))
    }

    companion object {
        private const val ALL_PATHS = "/**"
        private const val MIN_SECRET_BYTES = 32
        private val ALLOWED_HEADERS = listOf(CorsConfiguration.ALL)
        private val ALLOWED_METHODS = listOf(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
        )
    }
}
