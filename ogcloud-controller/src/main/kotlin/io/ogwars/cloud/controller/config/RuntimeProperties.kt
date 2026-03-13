package io.ogwars.cloud.controller.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ogcloud.runtime")
data class RuntimeProperties(
    val bucket: String = "ogcloud-runtime",
    val fillBaseUrl: String = "https://fill.papermc.io/v3",
    val userAgent: String = "ogcloud-controller/1.0 (https://github.com/Jevzo/ogcloud)",
    val paperProject: String = "paper",
    val modernPaperVersion: String = "1.21.11",
    val legacyPaperVersion: String = "1.8.8",
    val legacyPaperBuild: Int = 445,
    val velocityProject: String = "velocity",
)
