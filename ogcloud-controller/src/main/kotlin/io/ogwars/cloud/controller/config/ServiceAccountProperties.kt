package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.auth.service-account")
data class ServiceAccountProperties(
    val email: String,
    val password: String
)
