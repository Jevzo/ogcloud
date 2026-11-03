package io.ogwars.cloud.images

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.domain.runtime.Platform
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Path
import java.time.Duration

/**
 * Every server image boots with OgCloud's plugin and the bundle plugins it depends on (D-0139). Nothing answers at
 * the gateway address, so the plugin enables and keeps reconnecting, as in a pod whose gateway is down.
 */
class ServerImagesSmokeTest {
    @ParameterizedTest
    @MethodSource("profiles")
    fun `the server boots with OgCloud enabled`(profile: RuntimeProfile) {
        val checks = ImageChecks.of(profile.platform)
        GenericContainer(DockerImageName.parse(imageOf(profile))).use { server ->
            seed(server, profile)
            server
                .withEnv(environment(profile))
                // The template loader fills /data in a pod; here the seed is copied as the image's user.
                .withCreateContainerCmdModifier {
                    it.withEntrypoint(
                        "sh",
                        "-c",
                        "cp -R $SEED/. /data && exec $ENTRYPOINT",
                    )
                }.waitingFor(Wait.forLogMessage(DONE, 1).withStartupTimeout(STARTUP))
                .start()

            val logs = server.logs
            logs shouldContain checks.enabled
            checks.failures.forEach { logs shouldNotContain it }
        }
    }

    private fun seed(
        server: GenericContainer<*>,
        profile: RuntimeProfile,
    ) {
        val plugins = Path.of(System.getProperty("ogcloud.smoke.plugin.${profile.platform.name.lowercase()}"))
        server.withCopyFileToContainer(MountableFile.forHostPath(plugins), "$SEED/plugins/ogcloud.jar")
        RuntimeProfiles.PLUGINS
            .filter { profile.platform in it.platforms && it.id in BUNDLE_DEPENDENCIES }
            .forEach {
                server.withCopyFileToContainer(
                    MountableFile.forHostPath(BundlePlugins.download(it)),
                    "$SEED/plugins/${it.file}",
                )
            }
        if (profile.platform !=
            Platform.VELOCITY
        ) {
            server.withCopyToContainer(Transferable.of("eula=true\n"), "$SEED/eula.txt")
        }
        server
            .withCopyFileToContainer(MountableFile.forClasspathResource("smoke-ca.crt"), "$SECRETS/ca.crt")
            .withCopyToContainer(Transferable.of("smoke-token\n"), "$SECRETS/token")
            .withCopyToContainer(Transferable.of("smoke-forwarding\n"), "$SECRETS/forwarding")
    }

    private fun environment(profile: RuntimeProfile) =
        mapOf(
            PodEnv.SERVER_ID to "0192d2c10000700080000000000000aa",
            PodEnv.GROUP_ID to "smoke",
            PodEnv.GROUP_TYPE to if (profile.platform == Platform.VELOCITY) "PROXY" else "DYNAMIC",
            PodEnv.MAX_PLAYERS to "20",
            PodEnv.GATEWAY_ADDRESS to "gateway.invalid:8443",
            PodEnv.GATEWAY_TOKEN_FILE to "$SECRETS/token",
            PodEnv.GATEWAY_CA_FILE to "$SECRETS/ca.crt",
            PodEnv.FORWARDING_TOKENS_FILE to "$SECRETS/forwarding",
            PodEnv.PROFILE_ID to profile.id,
        )

    private fun imageOf(profile: RuntimeProfile) =
        "${System.getProperty("ogcloud.smoke.registry")}/${profile.platform.name.lowercase()}:" +
            "${profile.version}-${System.getProperty("ogcloud.smoke.version")}"

    companion object {
        private const val SEED = "/seed"
        private const val SECRETS = "/seed-secrets"
        private const val ENTRYPOINT = "/opt/ogcloud/start.sh"
        private const val DONE = ".*Done \\(.*"
        private val STARTUP = Duration.ofMinutes(3)

        /** Bundle plugins OgCloud's plugins cannot start without; the Via plugins are optional on the proxy. */
        private val BUNDLE_DEPENDENCIES = setOf("packetevents")

        @JvmStatic
        fun profiles() = RuntimeProfiles.ALL.map { Named.of(it.id, it) }
    }
}

/** What a platform logs when OgCloud's plugin enabled, and what it logs when it did not. */
private data class ImageChecks(
    val enabled: String,
    val failures: List<String>,
) {
    companion object {
        private val bukkit =
            ImageChecks("[OgCloud] Enabling OgCloud", listOf("Error occurred while enabling OgCloud", "Could not load"))

        fun of(platform: Platform) =
            when (platform) {
                Platform.PAPER, Platform.PANDASPIGOT -> {
                    bukkit
                }

                Platform.VELOCITY -> {
                    ImageChecks(
                        "Loaded plugin ogcloud",
                        listOf("Couldn't pass ProxyInitializeEvent to ogcloud"),
                    )
                }

                else -> {
                    throw IllegalArgumentException("No smoke checks for $platform yet")
                }
            }
    }
}
