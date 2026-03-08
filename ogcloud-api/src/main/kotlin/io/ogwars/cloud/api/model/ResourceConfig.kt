package io.ogwars.cloud.api.model

data class ResourceConfig(
    val memoryRequest: String,
    val memoryLimit: String,
    val cpuRequest: String,
    val cpuLimit: String,
)
