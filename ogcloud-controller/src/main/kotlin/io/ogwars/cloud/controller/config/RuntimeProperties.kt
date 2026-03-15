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
    val modernPaperPluginUrl: String =
        "https://github.com/Jevzo/ogcloud/releases/download/v1.2.3-pre/ogcloud-paper-plugin-1.2.3.jar",
    val legacyPaperPluginUrl: String =
        "https://github.com/Jevzo/ogcloud/releases/download/v1.2.3-pre/ogcloud-paper-plugin-1.2.3.jar",
    val velocityPluginUrl: String =
        "https://github.com/Jevzo/ogcloud/releases/download/v1.2.3-pre/ogcloud-velocity-plugin-1.2.3.jar",
    val viaVersionUrl: String =
        "https://hangarcdn.papermc.io/plugins/ViaVersion/ViaVersion/versions/5.7.2/PAPER/ViaVersion-5.7.2.jar",
    val viaVersionDefaultServerProtocol: Int = 774,
    val viaBackwardsUrl: String =
        "https://hangarcdn.papermc.io/plugins/ViaVersion/ViaBackwards/versions/5.7.2/PAPER/ViaBackwards-5.7.2.jar",
    val viaRewindUrl: String =
        "https://hangarcdn.papermc.io/plugins/ViaVersion/ViaRewind/versions/4.0.15/PAPER/ViaRewind-4.0.15.jar",
    val bungeeGuardUrl: String =
        "https://ci.lucko.me/job/BungeeGuard/lastStableBuild/artifact/bungeeguard-universal/target/BungeeGuard.jar",
    val protocolLibUrl: String =
        "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar",
)
