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
    /** The third-party plugins in this profile's runtime bundle. */
    val plugins: List<RuntimePlugin> get() = RuntimeProfiles.PLUGINS.filter { platform in it.platforms }

    /** The smallest memory limit a group of this platform may set, if the catalog names one (D-0177). */
    val minimumMemoryBytes: Long? get() = RuntimeProfiles.MINIMUM_MEMORY_BYTES[platform]

    companion object {
        fun find(id: String): RuntimeProfile? = RuntimeProfiles.ALL.find { it.id == id }
    }
}
