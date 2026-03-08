package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.kubernetes")
data class KubernetesProperties(
    val namespace: String,
)
