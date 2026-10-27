package io.ogwars.cloud.controller.runtime

import io.ogwars.cloud.contracts.internal.v1.RuntimeBundle
import io.ogwars.cloud.contracts.internal.v1.RuntimeBundleArtifact
import io.ogwars.cloud.controller.config.RuntimeProperties
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.runtime.RuntimeArtifact
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.ObjectStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.RuntimeArtifactStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.HexFormat

/**
 * Keeps every profile's runtime bundle in object storage (D-0079): uploads what changed under content-addressed
 * keys, publishes the manifest read model (D-0080) and, when servers run an older bundle, queues a rollout job.
 * Replaced objects stay for running pods; the cleanup loop removes them once unreferenced (D-0043).
 */
@Component
class RuntimeBundles(
    private val plan: BundlePlan,
    private val fetcher: ArtifactFetcher,
    private val objects: ObjectStore,
    private val artifacts: RuntimeArtifactStore,
    private val readModels: ReadModelStore,
    private val servers: ServerStateStore,
    private val jobs: JobStore,
    private val ids: IdGenerator,
    private val properties: RuntimeProperties,
    private val clock: Clock,
    private val meters: MeterRegistry,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)

    override val name = "runtime-bundles"
    override val interval: Duration get() = properties.syncInterval

    override fun runOnce() {
        RuntimeProfiles.ALL.forEach { profile ->
            // One profile's broken download must not keep the others from updating.
            try {
                sync(profile)
            } catch (e: Exception) {
                log.error("Runtime bundle of {} is not updated", profile.id, e)
                Counter
                    .builder(FAILURES_METRIC)
                    .tag("profile", profile.id)
                    .register(meters)
                    .increment()
            }
        }
    }

    private fun sync(profile: RuntimeProfile) {
        val stored = artifacts.findByProfile(profile.id).associateBy { it.path }
        val sources = plan.sourcesFor(profile)
        val current =
            sources.map { source ->
                stored[source.path]?.takeIf { upToDate(source, it) }
                    ?: store(profile, source)
            }
        val removed = stored.keys - sources.map { it.path }.toSet()
        removed.forEach { artifacts.delete(profile.id, it) }
        val manifest = manifest(profile, current)
        // Compared with the published manifest, so a run that failed after storing still queues the rollout next time.
        val published = readModels.get(ReadModelKind.RUNTIME_BUNDLE, profile.id)?.let(RuntimeBundle::parseFrom)
        if (published != null && published.digest != manifest.digest) requestRollout(profile)
        readModels.put(ReadModelKind.RUNTIME_BUNDLE, profile.id, manifest.toByteArray())
    }

    private fun upToDate(
        source: BundleSource,
        existing: RuntimeArtifact,
    ): Boolean =
        when (source) {
            is BundleSource.Download -> existing.sourceUrl == source.url.toString()
            is BundleSource.LocalFile -> existing.sha256 == Files.newInputStream(source.file).use { sha256(it) }
            is BundleSource.Generated -> existing.sha256 == sha256(source.content.inputStream())
        }

    private fun store(
        profile: RuntimeProfile,
        source: BundleSource,
    ): RuntimeArtifact =
        when (source) {
            is BundleSource.Download -> withVerifiedDownload(source) { file -> storeFile(profile, source, file) }
            is BundleSource.LocalFile -> storeFile(profile, source, source.file)
            is BundleSource.Generated -> storeBytes(profile, source, source.content)
        }

    private fun storeFile(
        profile: RuntimeProfile,
        source: BundleSource,
        file: Path,
    ): RuntimeArtifact {
        val sha256 = Files.newInputStream(file).use(::sha256)
        val key = objectKey(profile, source, sha256)
        Files.newInputStream(file).use { objects.upload(key, it) }
        return record(profile, source, key, sha256, Files.size(file))
    }

    private fun storeBytes(
        profile: RuntimeProfile,
        source: BundleSource,
        content: ByteArray,
    ): RuntimeArtifact {
        val sha256 = sha256(content.inputStream())
        val key = objectKey(profile, source, sha256)
        objects.upload(key, content.inputStream())
        return record(profile, source, key, sha256, content.size.toLong())
    }

    /** Downloads once to a temporary file and checks the pinned SHA-512, so exactly the verified bytes are stored. */
    private fun <T> withVerifiedDownload(
        source: BundleSource.Download,
        use: (Path) -> T,
    ): T {
        val file = Files.createTempFile("ogcloud-bundle-", ".jar")
        try {
            fetcher.open(source.url).use { Files.copy(it, file, StandardCopyOption.REPLACE_EXISTING) }
            val actual = Files.newInputStream(file).use { digest(it, SHA_512) }
            check(actual == source.sha512) { "${source.url} has SHA-512 $actual, the catalog pins ${source.sha512}" }
            return use(file)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun record(
        profile: RuntimeProfile,
        source: BundleSource,
        key: String,
        sha256: String,
        size: Long,
    ): RuntimeArtifact {
        val artifact = RuntimeArtifact(profile.id, source.path, key, sourceOf(source), sha256, size, clock.instant())
        artifacts.save(artifact)
        log.info("Stored {} of {} as {}", source.path, profile.id, key)
        return artifact
    }

    private fun objectKey(
        profile: RuntimeProfile,
        source: BundleSource,
        sha256: String,
    ) = "runtime/${profile.id}/$sha256/${source.path.substringAfterLast('/')}"

    private fun requestRollout(profile: RuntimeProfile) {
        if (servers.listAll().none { it.value.profileId == profile.id }) return
        val alreadyQueued =
            jobs.findActive().any {
                it.kind == JobKinds.BUNDLE_ROLLOUT &&
                    it.payload[JobKinds.PROFILE_ID] == profile.id
            }
        if (alreadyQueued) return
        val job =
            Job(
                ids.next(),
                JobKinds.BUNDLE_ROLLOUT,
                JobState.QUEUED,
                mapOf(JobKinds.PROFILE_ID to profile.id),
                emptyMap(),
                0,
                0,
                "",
                ACTOR,
                clock.instant(),
            )
        jobs.create(job)
        log.info("Bundle of {} changed; queued rollout job {}", profile.id, job.id)
    }

    private fun manifest(
        profile: RuntimeProfile,
        current: List<RuntimeArtifact>,
    ): RuntimeBundle {
        val sorted = current.sortedBy { it.path }
        val digest = sha256(sorted.joinToString("\n") { "${it.path}:${it.sha256}" }.byteInputStream())
        return RuntimeBundle
            .newBuilder()
            .setProfileId(profile.id)
            .setDigest(digest)
            .addAllArtifacts(
                sorted.map {
                    RuntimeBundleArtifact
                        .newBuilder()
                        .setPath(it.path)
                        .setObjectKey(it.objectKey)
                        .setSha256(it.sha256)
                        .setSizeBytes(it.sizeBytes)
                        .build()
                },
            ).build()
    }

    private fun sourceOf(source: BundleSource): String =
        when (source) {
            is BundleSource.Download -> source.url.toString()
            is BundleSource.LocalFile -> "image:${source.file.fileName}"
            is BundleSource.Generated -> GENERATED
        }

    private fun sha256(input: InputStream): String = digest(input, SHA_256)

    private fun digest(
        input: InputStream,
        algorithm: String,
    ): String {
        val digest = MessageDigest.getInstance(algorithm)
        input.use { DigestInputStream(it, digest).transferTo(OutputSink) }
        return HEX.formatHex(digest.digest())
    }

    /** Discards bytes that only need to be hashed. */
    private object OutputSink : java.io.OutputStream() {
        override fun write(b: Int) = Unit

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = Unit
    }

    private companion object {
        val ACTOR = Actor.system("runtime-bundles")
        val HEX: HexFormat = HexFormat.of()
        const val GENERATED = "generated"
        const val SHA_256 = "SHA-256"
        const val SHA_512 = "SHA-512"
        const val FAILURES_METRIC = "ogcloud.controller.runtime.sync.failures"
    }
}
