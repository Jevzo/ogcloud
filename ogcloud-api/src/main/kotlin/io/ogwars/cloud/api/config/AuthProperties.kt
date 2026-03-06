package io.ogwars.cloud.api.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "ogcloud.auth")
data class AuthProperties(
    val jwtSecret: String,
    val accessTokenLifetime: Duration,
    val refreshTokenLifetime: Duration,
    val linkOtpLifetime: Duration
)
