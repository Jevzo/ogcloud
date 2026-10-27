package io.ogwars.cloud.controller.template

import io.ogwars.cloud.contracts.internal.v1.ActiveTemplate
import io.ogwars.cloud.controller.kube.PodGateway
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.ogwars.cloud.domain.store.TemplateVersionStore
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

data class RegisteredVersion(
    val id: UUID,
    val promoted: Boolean,
)

/**
 * Template versions are immutable (D-0042): a push adds one, STATIC groups start from it at once, and the API
 * promotes or rolls back DYNAMIC groups by changing the group. The active one is published for the gateway (D-0081).
 */
@Component
class TemplateVersions(
    private val servers: ServerStateStore,
    private val groups: GroupStore,
    private val templates: TemplateVersionStore,
    private val readModels: ReadModelStore,
    private val pods: PodGateway,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    /**
     * A template agent's finished upload; [serverId] comes from its authenticated session. A STATIC server pushes
     * after its server container stopped, when its live record may be gone, so its pod's labels name the group.
     * A retried registration returns the version it created the first time.
     */
    fun register(
        serverId: UUID,
        objectKey: String,
        sha256: String,
        sizeBytes: Long,
    ): RegisteredVersion {
        val groupId =
            servers.get(serverId)?.value?.groupId ?: pods.groupOfServerPod(serverId)
                ?: throw NoSuchElementException("Server $serverId has neither a record nor a pod")
        require(objectKeyPattern(groupId).matches(objectKey)) {
            "Uploads of $groupId must be named $PREFIX/$groupId/<uuid>$ARCHIVE"
        }
        require(sizeBytes > 0) { "An upload must not be empty" }
        val group = groups.find(groupId)?.value ?: throw NoSuchElementException("Group $groupId no longer exists")
        templates.findByGroup(groupId).find { it.objectKey == objectKey }?.let {
            return RegisteredVersion(
                it.id,
                group.activeTemplateVersionId == it.id,
            )
        }
        val version =
            TemplateVersion(
                ids.next(),
                group.id,
                objectKey,
                sha256,
                sizeBytes,
                TemplateSource.PUSH,
                AGENT,
                clock.instant(),
            )
        templates.add(version)
        val promoted = group.type == GroupType.STATIC
        if (promoted) promote(group.id, version.id)
        return RegisteredVersion(version.id, promoted)
    }

    /** Makes [versionId] the group's active template and republishes it. */
    fun promote(
        groupId: GroupId,
        versionId: UUID,
    ) {
        repeat(MAX_ATTEMPTS) {
            val current = groups.find(groupId) ?: throw NoSuchElementException("Group $groupId no longer exists")
            val updated = current.value.copy(activeTemplateVersionId = versionId)
            try {
                groups.update(current.revision, updated)
                publishActive(updated)
                return
            } catch (e: StaleUpdateException) {
                // An admin changed the group meanwhile; reread and apply the promotion on top.
            }
        }
        throw IllegalStateException("Group $groupId kept changing; promotion of $versionId gave up")
    }

    /** Writes the read model the gateway serves pods from; groups without a template have none. */
    fun publishActive(group: Group) {
        val version = group.activeTemplateVersionId?.let(templates::find)
        if (version == null) {
            readModels.delete(ReadModelKind.ACTIVE_TEMPLATE, group.id.value)
            return
        }
        val active =
            ActiveTemplate
                .newBuilder()
                .setGroupId(group.id.value)
                .setTemplateVersionId(version.id.toString())
                .setObjectKey(version.objectKey)
                .setSha256(version.sha256)
                .setSizeBytes(version.sizeBytes)
                .build()
        readModels.put(ReadModelKind.ACTIVE_TEMPLATE, group.id.value, active.toByteArray())
    }

    private companion object {
        const val PREFIX = "templates"
        const val ARCHIVE = ".tar.gz"

        /** No dot segments or extra slashes, so a version can never point into another group's prefix. */
        fun objectKeyPattern(groupId: GroupId) =
            Regex(
                "^$PREFIX/${Regex.escape(
                    groupId.value,
                )}/[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}${Regex.escape(ARCHIVE)}$",
            )

        const val MAX_ATTEMPTS = 5
        val AGENT = Actor.system("template-agent")
    }
}
