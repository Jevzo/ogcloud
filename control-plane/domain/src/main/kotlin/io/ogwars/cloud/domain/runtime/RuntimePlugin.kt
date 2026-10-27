package io.ogwars.cloud.domain.runtime

/** A third-party plugin the runtime bundle of each listed platform carries (D-0079). */
data class RuntimePlugin(
    val id: String,
    val platforms: Set<Platform>,
    /** The jar's name inside the server's plugins directory. */
    val file: String,
    val version: String,
    val url: String,
    val sha512: String,
)
