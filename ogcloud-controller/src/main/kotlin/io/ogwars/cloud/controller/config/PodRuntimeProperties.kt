package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.pod")
data class PodRuntimeProperties(
    val minioEndpoint: String,
    val minioAccessKey: String,
    val minioSecretKey: String,
    val kafkaBrokers: String,
    val kafkaConsumerRestartInitialBackoffMs: String,
    val kafkaConsumerRestartMaxBackoffMs: String,
    val kafkaConsumerRestartJitterMs: String,
    val redisHost: String,
    val redisPort: String,
    val mongodbUri: String,
    val apiUrl: String,
    val apiEmail: String,
    val apiPassword: String,
)
