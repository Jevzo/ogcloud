package io.ogwars.cloud.controller.model

data class ResourceConfig(
    val memoryRequest: String,
    val memoryLimit: String,
    val cpuRequest: String,
    val cpuLimit: String,
)
