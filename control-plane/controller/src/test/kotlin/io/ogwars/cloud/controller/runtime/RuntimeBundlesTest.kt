package io.ogwars.cloud.controller.runtime

import io.ogwars.cloud.contracts.internal.v1.RuntimeBundle
import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.config.RuntimeProperties
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.runtime.Platform
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.persistence.JdbcJobStore
import io.ogwars.cloud.persistence.JsonCodec
import io.ogwars.cloud.persistence.TestDatabase
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

class RuntimeBundlesTest {
    private val f = LifecycleFixture(listOf(group(GroupType.PROXY, id = "proxy")))
    private val objects = InMemoryObjectStore()
    private val artifacts = InMemoryRuntimeArtifactStore()
    private val readModels = ValkeyReadModelStore(TestValkey.connection)
    private val jobs = JdbcJobStore(TestDatabase.jdbc, JsonCodec())
    private val pluginsDir = Files.createTempDirectory("ogcloud-plugins")
    private val viaUrl = URI("https://cdn.modrinth.com/data/test/ViaVersion.jar")
    private var viaBytes = "via-1".toByteArray()
    private var viaSha512 = sha512(viaBytes)
    private val downloads = mutableListOf<URI>()

    // The real catalog pins real jars; these tests plan a Velocity bundle with their own bytes and hashes.
    private val plan =
        BundlePlan { profile ->
            if (profile.platform != Platform.VELOCITY) {
                emptyList()
            } else {
                listOf(
                    BundleSource.LocalFile("plugins/ogcloud-velocity.jar", pluginsDir.resolve("ogcloud-velocity.jar")),
                    BundleSource.Download("plugins/ViaVersion.jar", viaUrl, viaSha512),
                    BundleSource.Generated("velocity.toml", GeneratedConfigs.velocityToml().toByteArray()),
                )
            }
        }
    private val bundles =
        RuntimeBundles(
            plan,
            { url ->
                downloads += url
                viaBytes.inputStream()
            },
            objects,
            artifacts,
            readModels,
            f.servers,
            jobs,
            { UUID.randomUUID() },
            RuntimeProperties(pluginsDir = pluginsDir),
            f.clock,
            SimpleMeterRegistry(),
        )

    init {
        TestDatabase.jdbc.sql("delete from jobs").update()
        Files.writeString(pluginsDir.resolve("ogcloud-velocity.jar"), "ogcloud-1")
    }

    @Test
    fun `the first sync stores every file and publishes the manifest`() {
        bundles.runOnce()

        val manifest = RuntimeBundle.parseFrom(readModels.get(ReadModelKind.RUNTIME_BUNDLE, "velocity-4.3.0"))
        manifest.artifactsList.map { it.path } shouldContainExactly
            listOf("plugins/ViaVersion.jar", "plugins/ogcloud-velocity.jar", "velocity.toml")
        manifest.artifactsList.forEach {
            objects.objects
                .getValue(it.objectKey)
                .size
                .toLong() shouldBe it.sizeBytes
        }
        jobs.findActive() shouldBe emptyList()
    }

    @Test
    fun `an unchanged bundle downloads nothing again`() {
        bundles.runOnce()
        bundles.runOnce()

        downloads.size shouldBe 1
        objects.objects.size shouldBe 3
    }

    @Test
    fun `a changed plugin gets a new object and running proxies get one rollout`() {
        bundles.runOnce()
        f.request(group(GroupType.PROXY, id = "proxy"))
        val before = RuntimeBundle.parseFrom(readModels.get(ReadModelKind.RUNTIME_BUNDLE, "velocity-4.3.0")).digest

        Files.writeString(pluginsDir.resolve("ogcloud-velocity.jar"), "ogcloud-2")
        bundles.runOnce()
        bundles.runOnce()

        val after = RuntimeBundle.parseFrom(readModels.get(ReadModelKind.RUNTIME_BUNDLE, "velocity-4.3.0")).digest
        (after == before) shouldBe false
        objects.objects.size shouldBe 4
        jobs.findActive().map { it.kind to it.payload[JobKinds.PROFILE_ID] } shouldContainExactly
            listOf(JobKinds.BUNDLE_ROLLOUT to "velocity-4.3.0")
    }

    @Test
    fun `a rollout that failed to be queued is queued by the next run`() {
        bundles.runOnce()
        f.request(group(GroupType.PROXY, id = "proxy"))
        Files.writeString(pluginsDir.resolve("ogcloud-velocity.jar"), "ogcloud-2")
        val failingOnce =
            object : JobStore by jobs {
                var failures = 1

                override fun create(job: Job) {
                    if (failures-- > 0) throw IllegalStateException("database unavailable")
                    jobs.create(job)
                }
            }
        val flaky =
            RuntimeBundles(plan, {
                viaBytes.inputStream()
            }, objects, artifacts, readModels, f.servers, failingOnce, {
                UUID.randomUUID()
            }, RuntimeProperties(pluginsDir = pluginsDir), f.clock, SimpleMeterRegistry())

        flaky.runOnce()
        flaky.runOnce()

        jobs.findActive().map { it.kind } shouldContainExactly listOf(JobKinds.BUNDLE_ROLLOUT)
    }

    @Test
    fun `a download that does not match the pinned hash is not stored`() {
        viaSha512 = "0".repeat(128)

        bundles.runOnce()

        objects.objects.keys.none { it.endsWith("ViaVersion.jar") } shouldBe true
        readModels.get(ReadModelKind.RUNTIME_BUNDLE, "velocity-4.3.0") shouldBe null
    }

    @Test
    fun `the generated Velocity config forwards with BungeeGuard behind the PROXY protocol`() {
        val toml = GeneratedConfigs.velocityToml()

        toml shouldContain "player-info-forwarding-mode = \"bungeeguard\""
        toml shouldContain "haproxy-protocol = true"
        toml shouldContain "forwarding-secret-file = \"${GeneratedConfigs.FORWARDING_SECRET_FILE}\""
        GeneratedConfigs.viaVersionConfig() shouldContain "block-protocols: [\"<47\"]"
    }

    private fun sha512(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes))
}
