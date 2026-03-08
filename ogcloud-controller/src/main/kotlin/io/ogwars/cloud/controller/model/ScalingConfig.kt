package io.ogwars.cloud.controller.model

data class ScalingConfig(
    val minOnline: Int,
    val maxInstances: Int,
    val playersPerServer: Int,
    val scaleUpThreshold: Double,
    val scaleDownThreshold: Double,
    val cooldownSeconds: Int,
)
