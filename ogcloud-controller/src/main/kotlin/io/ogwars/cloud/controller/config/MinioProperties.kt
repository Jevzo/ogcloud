package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.minio")
data class MinioProperties(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
)
