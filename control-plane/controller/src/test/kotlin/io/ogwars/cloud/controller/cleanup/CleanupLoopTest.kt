package io.ogwars.cloud.controller.cleanup

import io.ogwars.cloud.controller.TestValkey
import io.ogwars.cloud.controller.config.CleanupProperties
import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.ogwars.cloud.controller.lifecycle.LifecycleFixture
import io.ogwars.cloud.controller.runtime.InMemoryObjectStore
import io.ogwars.cloud.controller.runtime.InMemoryRuntimeArtifactStore
import io.ogwars.cloud.controller.template.InMemoryTemplateVersionStore
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.retention.RetentionKind
import io.ogwars.cloud.domain.runtime.RuntimeArtifact
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.RetentionStore
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import io.ogwars.cloud.livestate.ValkeyReadModelStore
import io.ogwars.cloud.persistence.JdbcJobStore
import io.ogwars.cloud.persistence.JsonCodec
import io.ogwars.cloud.persistence.TestDatabase
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CleanupLoopTest {
    private val bedwars = group(id = "bedwars")
    private val smp = group(GroupType.STATIC, id = "smp")
    private val f = LifecycleFixture(listOf(group(id = "lobby"), bedwars, smp))
    private val objects = InMemoryObjectStore()
    private val templates = InMemoryTemplateVersionStore()
    private val artifacts = InMemoryRuntimeArtifactStore()
    private val readModels = ValkeyReadModelStore(TestValkey.connection)
    private val jobs = JdbcJobStore(TestDatabase.jdbc, JsonCodec())
    private val records = FakeRetentionStore(mapOf(RetentionKind.AUDIT_LOG to 2500))
    private val claims = PodSpecBuilder(PodProperties("ghcr.io/jevzo/ogcloud", "2.0.0", "gateway:9443"))

    init {
        TestDatabase.jdbc.sql("delete from jobs").update()
    }

    @Test
    fun `old records go in bounded batches`() {
        loop().runOnce()

        records.deletes shouldContainExactly listOf(1000, 1000, 500)
    }

    @Test
    fun `a group keeps its active template plus the three newest others`() {
        val versions = (0L..5L).map { template(bedwars.id, age = Duration.ofDays(10 - it)) }
        f.groupStore.update(0, bedwars.copy(activeTemplateVersionId = versions.first().id))

        loop().runOnce()

        templates.findByGroup(bedwars.id).map { it.id } shouldContainExactlyInAnyOrder
            listOf(versions[0], versions[3], versions[4], versions[5]).map { it.id }
        objects.objects.keys shouldBe templates.findByGroup(bedwars.id).map { it.objectKey }.toSet()
    }

    @Test
    fun `a deleted group is purged after its grace period with its volume and archives`() {
        template(smp.id, age = Duration.ofDays(1))
        f.pods.ensureVolumeClaim(claims.staticVolumeClaim(smp))
        readModels.put(ReadModelKind.GROUP, "smp", byteArrayOf(1))
        f.groupStore.softDelete(smp.id, f.clock.instant())
        f.clock.advance(Duration.ofDays(8))

        loop().runOnce()

        f.groupStore.deleted() shouldBe emptyList()
        objects.objects shouldBe emptyMap()
        f.pods.claims shouldBe emptyList()
        readModels.get(ReadModelKind.GROUP, "smp") shouldBe null
    }

    @Test
    fun `a deleted group within its grace period, or with servers left, stays`() {
        f.request(bedwars)
        f.groupStore.softDelete(bedwars.id, f.clock.instant())
        f.groupStore.softDelete(smp.id, f.clock.instant())
        f.clock.advance(Duration.ofDays(8))
        f.groupStore.restore(smp.id)
        f.groupStore.softDelete(smp.id, f.clock.instant())

        loop().runOnce()

        f.groupStore.deleted() shouldContainExactlyInAnyOrder listOf(bedwars.id, smp.id)
    }

    @Test
    fun `replaced runtime files go once they are old and no rollout uses them`() {
        objects.upload("runtime/paper-26.3/aaa/ogcloud-paper.jar", "old".byteInputStream())
        objects.upload("runtime/paper-26.3/bbb/ogcloud-paper.jar", "current".byteInputStream())
        objects.upload("runtime/velocity-4.3.0/ccc/ViaVersion.jar", "rolling out".byteInputStream())
        artifacts.save(
            RuntimeArtifact(
                "paper-26.3",
                "plugins/ogcloud-paper.jar",
                "runtime/paper-26.3/bbb/ogcloud-paper.jar",
                "image",
                "b".repeat(64),
                7,
                Instant.EPOCH,
            ),
        )
        jobs.create(
            Job(
                UUID.randomUUID(),
                JobKinds.BUNDLE_ROLLOUT,
                JobState.RUNNING,
                mapOf(JobKinds.PROFILE_ID to "velocity-4.3.0"),
                emptyMap(),
                0,
                1,
                "",
                ADMIN,
                Instant.EPOCH,
            ),
        )
        objects.now = f.clock.instant()
        objects.upload("runtime/paper-26.3/ddd/ogcloud-paper.jar", "uploading".byteInputStream())

        loop().runOnce()

        objects.objects.keys shouldContainExactlyInAnyOrder
            listOf(
                "runtime/paper-26.3/bbb/ogcloud-paper.jar",
                "runtime/velocity-4.3.0/ccc/ViaVersion.jar",
                "runtime/paper-26.3/ddd/ogcloud-paper.jar",
            )
    }

    @Test
    fun `a group deleted by an API whose clock runs ahead keeps its volume`() {
        f.pods.ensureVolumeClaim(claims.staticVolumeClaim(smp))
        f.groupStore.softDelete(smp.id, f.clock.instant().plus(Duration.ofMinutes(5)))

        loop().runOnce()

        f.pods.claims.map { it.metadata.name } shouldContainExactly listOf("static-smp")
    }

    @Test
    fun `unregistered template uploads and claims of unknown groups go`() {
        objects.upload("templates/bedwars/abandoned.tar.gz", "x".byteInputStream())
        f.pods.ensureVolumeClaim(claims.staticVolumeClaim(group(GroupType.STATIC, id = "ghost")))
        f.pods.ensureVolumeClaim(claims.staticVolumeClaim(smp))

        loop().runOnce()

        objects.objects shouldBe emptyMap()
        f.pods.claims.map { it.metadata.name } shouldContainExactly listOf("static-smp")
    }

    @Test
    fun `a dry run deletes nothing`() {
        objects.upload("templates/bedwars/abandoned.tar.gz", "x".byteInputStream())
        f.pods.ensureVolumeClaim(claims.staticVolumeClaim(group(GroupType.STATIC, id = "ghost")))

        loop(dryRun = true).runOnce()

        objects.objects.size shouldBe 1
        f.pods.claims.size shouldBe 1
        records.deletes shouldBe emptyList()
    }

    private fun loop(dryRun: Boolean = false) =
        CleanupLoop(
            records,
            f.groupStore,
            templates,
            artifacts,
            objects,
            f.servers,
            jobs,
            readModels,
            f.pods,
            CleanupProperties(dryRun = dryRun),
            f.clock,
            SimpleMeterRegistry(),
        )

    private fun template(
        groupId: GroupId,
        age: Duration,
    ): TemplateVersion {
        val id = UUID.randomUUID()
        val version =
            TemplateVersion(
                id,
                groupId,
                "templates/$groupId/$id.tar.gz",
                "a".repeat(64),
                1,
                TemplateSource.UPLOAD,
                ADMIN,
                f.clock.instant().minus(age),
            )
        templates.add(version)
        objects.upload(version.objectKey, "archive".byteInputStream())
        return version
    }

    /** Pretends [pending] old records per kind exist and records each batch it deletes. */
    private class FakeRetentionStore(
        pending: Map<RetentionKind, Int>,
    ) : RetentionStore {
        private val remaining = pending.toMutableMap()
        val deletes = mutableListOf<Int>()

        override val kinds = pending.keys

        override fun countOlderThan(
            kind: RetentionKind,
            cutoff: Instant,
        ) = remaining.getValue(kind).toLong()

        override fun deleteOlderThan(
            kind: RetentionKind,
            cutoff: Instant,
            limit: Int,
        ): Int {
            val batch = minOf(limit, remaining.getValue(kind))
            remaining[kind] = remaining.getValue(kind) - batch
            deletes += batch
            return batch
        }
    }

    private companion object {
        val ADMIN = Actor.system("test")
    }
}
