package io.ogwars.cloud.controller.cleanup

import io.ogwars.cloud.controller.config.CleanupProperties
import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.controller.kube.PodLabels
import io.ogwars.cloud.controller.kube.PodSpecBuilder
import io.ogwars.cloud.controller.leader.LeaderLoop
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.retention.RetentionPolicy
import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.ObjectStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.RetentionStore
import io.ogwars.cloud.domain.store.RuntimeArtifactStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Nothing piles up (D-0043): old logs and jobs, surplus template versions, purged groups, replaced runtime files,
 * orphan objects and volume claims, and abandoned uploads are deleted on the leader. Anything still referenced stays;
 * a dry run only counts.
 */
@Component
class CleanupLoop(
    private val retention: RetentionStore,
    private val groups: GroupStore,
    private val templates: TemplateVersionStore,
    private val artifacts: RuntimeArtifactStore,
    private val objects: ObjectStore,
    private val servers: ServerStateStore,
    private val jobs: JobStore,
    private val readModels: ReadModelStore,
    private val pods: PodGateway,
    private val properties: CleanupProperties,
    private val clock: Clock,
    private val meters: MeterRegistry,
) : LeaderLoop {
    private val log = LoggerFactory.getLogger(javaClass)
    private val policy = RetentionPolicy(properties.retention)

    override val name = "cleanup"
    override val interval: Duration get() = properties.interval

    override fun runOnce() {
        val now = clock.instant()
        category("records") { records(now) }
        category("template-versions") { templateVersions() }
        category("deleted-groups") { deletedGroups(now) }
        category("runtime-objects") { runtimeObjects(now) }
        category("template-objects") { templateObjects(now) }
        category("volume-claims") { orphanVolumeClaims() }
        category("multipart-uploads") {
            if (properties.dryRun) 0 else objects.abortStaleUploads(now.minus(properties.orphanGrace))
        }
    }

    private fun records(now: Instant): Int =
        retention.kinds.sumOf { kind ->
            val cutoff = policy.cutoff(kind, now)
            if (properties.dryRun) return@sumOf retention.countOlderThan(kind, cutoff).toInt()
            var deleted = 0
            repeat(properties.maxBatches) {
                val batch = retention.deleteOlderThan(kind, cutoff, properties.batchSize)
                deleted += batch
                if (batch < properties.batchSize) return@sumOf deleted
            }
            deleted
        }

    private fun templateVersions(): Int =
        groups.findAll().map { it.value }.sumOf { group ->
            val surplus =
                policy.templateVersionsToDelete(
                    templates.findByGroup(group.id),
                    group.activeTemplateVersionId,
                )
            if (!properties.dryRun) {
                surplus.forEach { version ->
                    objects.delete(version.objectKey)
                    templates.delete(version.id)
                }
            }
            surplus.size
        }

    /** Soft-deleted groups past their grace period, once none of their servers is left. */
    private fun deletedGroups(now: Instant): Int {
        val cutoff = now.minus(properties.retention.deletedGroupGrace)
        val due = groups.deletedBefore(cutoff).filter { servers.listByGroup(it).isEmpty() }
        if (properties.dryRun) return due.size
        return due.count { id -> purge(id, cutoff) }
    }

    private fun purge(
        id: GroupId,
        cutoff: Instant,
    ): Boolean {
        val objectKeys = templates.findByGroup(id).map { it.objectKey }
        if (!groups.purge(id, cutoff)) return false
        objectKeys.forEach(objects::delete)
        pods.deleteVolumeClaim(PodSpecBuilder.staticClaimName(id))
        listOf(ReadModelKind.GROUP, ReadModelKind.ACTIVE_TEMPLATE, ReadModelKind.NPCS_BY_GROUP).forEach {
            readModels.delete(it, id.value)
        }
        log.info("Purged group {} and {} template archives", id, objectKeys.size)
        return true
    }

    /** Files a bundle no longer references, once no rollout of that profile still runs (D-0079). */
    private fun runtimeObjects(now: Instant): Int {
        val rollingOut =
            jobs
                .findActive()
                .filter {
                    it.kind == JobKinds.BUNDLE_ROLLOUT
                }.mapNotNull { it.payload[JobKinds.PROFILE_ID] }
                .toSet()
        val referenced =
            RuntimeProfiles.ALL
                .flatMap { artifacts.findByProfile(it.id) }
                .map { it.objectKey }
                .toSet()
        val orphans =
            objects
                .list(RUNTIME_PREFIX)
                .filter { it.key !in referenced && it.lastModified.isBefore(now.minus(properties.orphanGrace)) }
                .filter { it.key.removePrefix(RUNTIME_PREFIX).substringBefore('/') !in rollingOut }
        return deleteObjects(orphans.map { it.key })
    }

    /** Archives no template version points at, e.g. uploads that were never registered. */
    private fun templateObjects(now: Instant): Int {
        val referenced = knownGroups().flatMap(templates::findByGroup).map { it.objectKey }.toSet()
        val orphans =
            objects.list(TEMPLATE_PREFIX).filter {
                it.key !in referenced &&
                    it.lastModified.isBefore(now.minus(properties.orphanGrace))
            }
        return deleteObjects(orphans.map { it.key })
    }

    /** Claims of groups that no longer exist at all; soft-deleted groups keep theirs until the purge. */
    private fun orphanVolumeClaims(): Int {
        val known = knownGroups().map { it.value }.toSet()
        val orphans = pods.listVolumeClaims().filter { it.metadata.labels?.get(PodLabels.GROUP) !in known }
        if (!properties.dryRun) orphans.forEach { pods.deleteVolumeClaim(it.metadata.name) }
        return orphans.size
    }

    /** Live and soft-deleted groups: everything that may still own objects or a volume. */
    private fun knownGroups(): List<GroupId> = groups.findAll().map { it.value.id } + groups.deleted()

    private fun deleteObjects(keys: List<String>): Int {
        if (!properties.dryRun) keys.forEach(objects::delete)
        return keys.size
    }

    // Categories are independent: one failing store must not stop the others.
    private fun category(
        name: String,
        clean: () -> Int,
    ) {
        try {
            val count = clean()
            if (count >
                0
            ) {
                log.info(
                    "Cleanup {} {} items in {}",
                    if (properties.dryRun) "would delete" else "deleted",
                    count,
                    name,
                )
            }
            Counter
                .builder(DELETED_METRIC)
                .tag("category", name)
                .tag("dry-run", properties.dryRun.toString())
                .register(meters)
                .increment(count.toDouble())
        } catch (e: Exception) {
            log.error("Cleanup of {} failed", name, e)
            Counter
                .builder(FAILURES_METRIC)
                .tag("category", name)
                .register(meters)
                .increment()
        }
    }

    private companion object {
        const val RUNTIME_PREFIX = "runtime/"
        const val TEMPLATE_PREFIX = "templates/"
        const val DELETED_METRIC = "ogcloud.controller.cleanup.deleted"
        const val FAILURES_METRIC = "ogcloud.controller.cleanup.failures"
    }
}
