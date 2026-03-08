package io.ogwars.cloud.api.config

import io.minio.MinioClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MinioConfig(
    @Value("\${ogcloud.minio.endpoint}") private val endpoint: String,
    @Value("\${ogcloud.minio.access-key}") private val accessKey: String,
    @Value("\${ogcloud.minio.secret-key}") private val secretKey: String,
) {
    @Bean
    fun minioClient(): MinioClient =
        MinioClient
            .builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .build()
}
