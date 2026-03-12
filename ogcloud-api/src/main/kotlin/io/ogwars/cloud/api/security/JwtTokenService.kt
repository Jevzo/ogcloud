package io.ogwars.cloud.api.security

import io.ogwars.cloud.api.config.AuthProperties
import io.ogwars.cloud.api.exception.InvalidCredentialsException
import io.ogwars.cloud.api.model.WebUserDocument
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class JwtTokenService(
    private val jwtEncoder: JwtEncoder,
    private val jwtDecoder: JwtDecoder,
    private val authProperties: AuthProperties,
    private val clock: Clock,
) {
    fun issueAccessToken(user: WebUserDocument): JwtAccessToken {
        val issuedAt = Instant.now(clock)
        val expiresAt = issuedAt.plus(authProperties.accessTokenLifetime)

        val claims =
            JwtClaimsSet
                .builder()
                .subject(user.id)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("type", ACCESS_TOKEN_TYPE)
                .claim("email", user.email)
                .claim("role", user.role.name)
                .build()

        val token =
            jwtEncoder.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    claims,
                ),
            )

        return JwtAccessToken(token.tokenValue, expiresAt)
    }

    fun parseAccessToken(token: String): Jwt {
        val jwt =
            try {
                jwtDecoder.decode(token)
            } catch (_: Exception) {
                throw InvalidCredentialsException()
            }

        if (jwt.getClaimAsString("type") != ACCESS_TOKEN_TYPE) {
            throw InvalidCredentialsException()
        }

        return jwt
    }

    companion object {
        private const val ACCESS_TOKEN_TYPE = "access"
    }
}

data class JwtAccessToken(
    val token: String,
    val expiresAt: Instant,
)
