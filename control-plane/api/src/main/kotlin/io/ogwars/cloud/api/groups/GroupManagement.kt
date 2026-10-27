package io.ogwars.cloud.api.groups

import io.ogwars.cloud.api.events.Announcements
import io.ogwars.cloud.api.jobs.JobManagement
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.runtime.ProfileRole
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.DeletedGroup
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.Revisioned
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/** `ogcloud.groups.*` */
@ConfigurationProperties("ogcloud.groups")
data class GroupProperties(
    /** Custom server images must start with one of these; the controller enforces the same list. */
    val allowedImagePrefixes: List<String> = emptyList(),
)

/** Groups: committed to PostgreSQL, then announced for the controller's read models (D-0100). */
@Service
class GroupManagement(
    private val groups: GroupStore,
    private val jobs: JobManagement,
    private val announcements: Announcements,
    private val properties: GroupProperties,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    fun list(): List<Revisioned<Group>> = groups.findAll()

    fun deleted(): List<DeletedGroup> = groups.findDeleted()

    fun get(id: GroupId): Revisioned<Group> =
        groups.find(id) ?: throw NoSuchElementException("Group $id does not exist")

    fun create(
        actor: Actor,
        id: GroupId,
        type: GroupType,
        settings: GroupSettings,
    ): Revisioned<Group> {
        val group = validated(settings.toGroup(id, type, activeTemplateVersionId = null))
        groups.create(group)
        announcements.group(actor, id)
        return get(id)
    }

    fun update(
        actor: Actor,
        id: GroupId,
        expectedVersion: Long,
        settings: GroupSettings,
    ): Revisioned<Group> {
        val current = get(id).value
        // Templates are promoted by their own endpoints, never through settings.
        groups.update(expectedVersion, validated(settings.toGroup(id, current.type, current.activeTemplateVersionId)))
        announcements.group(actor, id)
        return get(id)
    }

    /**
     * Soft-deletes the group (restorable until its purge, D-0043) and starts the job that stops its servers; both
     * commit together. The network's default group cannot go.
     */
    fun delete(
        actor: Actor,
        id: GroupId,
    ): Job {
        val job =
            checkNotNull(
                transactions.execute {
                    if (!groups.softDelete(
                            id,
                            clock.instant(),
                        )
                    ) {
                        throw NoSuchElementException("Group $id does not exist")
                    }
                    jobs.create(actor, JobKinds.GROUP_DELETE, mapOf(JobKinds.GROUP_ID to id.value))
                },
            )
        announcements.group(actor, id, deleted = true)
        return jobs.announce(actor, job)
    }

    fun restore(
        actor: Actor,
        id: GroupId,
    ): Revisioned<Group> {
        if (!groups.restore(id)) throw NoSuchElementException("No deleted group $id")
        announcements.group(actor, id)
        return get(id)
    }

    /** Replaces every server of the group; like v1, only while the group is in maintenance. */
    fun restart(
        actor: Actor,
        id: GroupId,
        batchSize: Int?,
    ): Job {
        if (!get(
                id,
            ).value.maintenance
        ) {
            throw ConflictException("Turn on maintenance for group $id before restarting it")
        }
        return jobs.start(actor, JobKinds.GROUP_RESTART, mapOf(JobKinds.GROUP_ID to id.value) + batchPayload(batchSize))
    }

    private fun validated(group: Group): Group {
        val profile =
            RuntimeProfile.find(group.profileId)
                ?: throw IllegalArgumentException("Unknown runtime profile '${group.profileId}'")
        require((group.type == GroupType.PROXY) == (profile.role == ProfileRole.PROXY)) {
            "A ${group.type} group cannot run the ${profile.role} profile ${profile.id}"
        }
        group.imageOverride?.let { override ->
            require(properties.allowedImagePrefixes.any(override.image::startsWith)) {
                "Image ${override.image} is not allowed; allowed prefixes: ${properties.allowedImagePrefixes}"
            }
        }
        return group
    }
}

/** The optional batch size of a restart job; must be positive. */
fun batchPayload(batchSize: Int?): Map<String, String> {
    require(batchSize == null || batchSize > 0) { "batchSize must be positive" }
    return batchSize?.let { mapOf(JobKinds.BATCH_SIZE to it.toString()) }.orEmpty()
}
