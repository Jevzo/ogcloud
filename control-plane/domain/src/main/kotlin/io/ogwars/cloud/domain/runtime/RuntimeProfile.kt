package io.ogwars.cloud.domain.runtime

enum class Platform {
    PAPER,
    PANDASPIGOT,
    VELOCITY,
    FABRIC,
    NEOFORGE,
}

enum class ProfileRole {
    BACKEND,
    PROXY,
}

/**
 * One runnable server platform version from contracts/runtime-profiles.yaml (D-0006).
 * [protocol] is the backend's native Minecraft protocol number, 0 for proxies.
 */
data class RuntimeProfile(
    val id: String,
    val platform: Platform,
    val role: ProfileRole,
    val version: String,
    val protocol: Int,
    val javaVersion: Int,
) {
    companion object {
        fun find(id: String): RuntimeProfile? = RuntimeProfiles.ALL.find { it.id == id }
    }
}
