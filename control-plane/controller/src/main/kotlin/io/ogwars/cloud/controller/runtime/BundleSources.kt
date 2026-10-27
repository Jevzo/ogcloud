package io.ogwars.cloud.controller.runtime

import io.ogwars.cloud.controller.config.RuntimeProperties
import io.ogwars.cloud.domain.runtime.Platform
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Path

/** Where one file of a runtime bundle comes from; [path] is relative to the server directory. */
sealed interface BundleSource {
    val path: String

    /** A third-party jar, checked against the SHA-512 pinned in the catalog. */
    data class Download(
        override val path: String,
        val url: URI,
        val sha512: String,
    ) : BundleSource

    /** OgCloud's own plugin from the controller image. */
    data class LocalFile(
        override val path: String,
        val file: Path,
    ) : BundleSource

    class Generated(
        override val path: String,
        val content: ByteArray,
    ) : BundleSource
}

/** Which files a profile's bundle holds (D-0079). */
fun interface BundlePlan {
    fun sourcesFor(profile: RuntimeProfile): List<BundleSource>
}

@Component
class CatalogBundlePlan(
    private val properties: RuntimeProperties,
) : BundlePlan {
    override fun sourcesFor(profile: RuntimeProfile): List<BundleSource> {
        val ownPlugin = "ogcloud-${profile.platform.name.lowercase()}.jar"
        val sources =
            listOf(BundleSource.LocalFile("$PLUGINS/$ownPlugin", properties.pluginsDir.resolve(ownPlugin))) +
                profile.plugins.map { BundleSource.Download("$PLUGINS/${it.file}", URI(it.url), it.sha512) }
        return if (profile.platform == Platform.VELOCITY) {
            sources +
                listOf(
                    BundleSource.Generated(VELOCITY_CONFIG, GeneratedConfigs.velocityToml().toByteArray()),
                    BundleSource.Generated(VIAVERSION_CONFIG, GeneratedConfigs.viaVersionConfig().toByteArray()),
                )
        } else {
            sources
        }
    }

    private companion object {
        const val PLUGINS = "plugins"
        const val VELOCITY_CONFIG = "velocity.toml"
        const val VIAVERSION_CONFIG = "plugins/viaversion/config.yml"
    }
}
