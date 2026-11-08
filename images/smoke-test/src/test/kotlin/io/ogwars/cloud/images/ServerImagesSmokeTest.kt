package io.ogwars.cloud.images

import io.ogwars.cloud.contracts.PodEnv
import io.ogwars.cloud.domain.runtime.Platform
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import io.kotest.matchers.shouldBe
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
            if (profile.platform == Platform.VELOCITY) {
                // The proxy forwards with the newest token (D-0065).
                server.execInContainer("cat", VELOCITY_SECRET).stdout shouldBe "smoke-forwarding"
            }
        }
    }

    private fun seed(
        server: GenericContainer<*>,
        profile: RuntimeProfile,
    ) {
        // The jar the controller would put into the bundle (D-0079), as `mise run images:build` staged it.
        val plugin = Path.of(property("artifacts"), "plugins", "ogcloud-${profile.platform.name.lowercase()}.jar")
        server.withCopyFileToContainer(MountableFile.forHostPath(plugin), "$SEED/plugins/ogcloud.jar")
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
            .withCopyToContainer(Transferable.of("old-forwarding\nsmoke-forwarding\n\n"), "$SECRETS/forwarding")
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
        ) +
            if (profile.platform ==
                Platform.VELOCITY
            ) {
                mapOf(PodEnv.FORWARDING_SECRET_FILE to VELOCITY_SECRET)
            } else {
                emptyMap()
            }

    private fun imageOf(profile: RuntimeProfile) =
        "${property("registry")}/${profile.platform.name.lowercase()}:${profile.version}-${property("version")}"

    companion object {
        private const val SEED = "/seed"
        private const val SECRETS = "/seed-secrets"
        private const val ENTRYPOINT = "/opt/ogcloud/start.sh"

        // Velocity's default config, which the smoke test keeps, reads its secret from here.
        private const val VELOCITY_SECRET = "/data/forwarding.secret"
        private const val DONE = ".*Done \\(.*"
        private val STARTUP = Duration.ofMinutes(3)

        /** Bundle plugins OgCloud's plugins cannot start without; the Via plugins are optional on the proxy. */
        private val BUNDLE_DEPENDENCIES = setOf("packetevents")

        @JvmStatic
        fun profiles() = RuntimeProfiles.ALL.map { Named.of(it.id, it) }
    }
}

/** The smoke settings Gradle passes, set by `mise run check:images`. */
internal fun property(name: String): String =
    checkNotNull(
        System.getProperty("ogcloud.smoke.$name"),
    ) { "ogcloud.smoke.$name is not set; run mise run check:images" }

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
