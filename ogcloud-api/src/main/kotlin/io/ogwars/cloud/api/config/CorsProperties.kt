package io.ogwars.cloud.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = DEFAULT_ALLOWED_ORIGINS
) {
    companion object {
        private val DEFAULT_ALLOWED_ORIGINS = listOf("http://localhost:5173")
    }
}
