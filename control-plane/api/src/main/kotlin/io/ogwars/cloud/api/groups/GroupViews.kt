package io.ogwars.cloud.api.groups

import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.ImageOverride
import io.ogwars.cloud.domain.group.ResourceSpec
import io.ogwars.cloud.domain.group.ScalingPolicy
import io.ogwars.cloud.domain.store.DeletedGroup
import io.ogwars.cloud.domain.store.Revisioned
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ScalingView(
    val minOnline: Int,
    val maxInstances: Int,
    val playersPerServer: Int,
    val scaleUpThreshold: Double,
    val scaleDownThreshold: Double,
    val cooldownSeconds: Long,
) {
    fun toDomain() =
        ScalingPolicy(
            minOnline,
            maxInstances,
            playersPerServer,
            scaleUpThreshold,
            scaleDownThreshold,
            Duration.ofSeconds(cooldownSeconds),
        )
}

data class ResourcesView(
    val memoryRequest: String,
    val memoryLimit: String,
    val cpuRequest: String,
    val cpuLimit: String?,
) {
    fun toDomain() = ResourceSpec(memoryRequest, memoryLimit, cpuRequest, cpuLimit)
}

data class ImageView(
    val image: String,
    val pullSecretRef: String?,
) {
    fun toDomain() = ImageOverride(image, pullSecretRef)
}

/** The fields a caller sets; the type is fixed once the group exists. */
data class GroupSettings(
    val profileId: String,
    val scaling: ScalingView,
    val drainTimeoutSeconds: Long,
    val maintenance: Boolean,
    val resources: ResourcesView,
    val jvmFlags: String,
    val image: ImageView?,
    val storageSize: String?,
)

data class CreateGroupRequest(
    val id: String,
    val type: GroupType,
    val settings: GroupSettings,
)

data class UpdateGroupRequest(
    val settings: GroupSettings,
    /** The version read; a stale one is refused with 409 (D-0069). */
    val version: Long,
)

data class GroupView(
    val id: String,
    val type: GroupType,
    val settings: GroupSettings,
    val activeTemplateVersionId: UUID?,
    val version: Long,
)

data class DeletedGroupView(
    val group: GroupView,
    val deletedAt: Instant,
)

data class RestartRequest(
    /** Servers replaced at once; the controller's default when absent. */
    val batchSize: Int?,
)

fun GroupSettings.toGroup(
    id: GroupId,
    type: GroupType,
    activeTemplateVersionId: UUID?,
) = Group(
    id = id,
    type = type,
    profileId = profileId,
    scaling = scaling.toDomain(),
    drainTimeout = Duration.ofSeconds(drainTimeoutSeconds),
    maintenance = maintenance,
    resources = resources.toDomain(),
    jvmFlags = jvmFlags,
    imageOverride = image?.toDomain(),
    storageSize = storageSize,
    activeTemplateVersionId = activeTemplateVersionId,
)

fun Revisioned<Group>.toView() = value.toView(revision)

fun DeletedGroup.toView() = DeletedGroupView(group.toView(version = 0), deletedAt)

private fun Group.toView(version: Long) =
    GroupView(
        id = id.value,
        type = type,
        settings =
            GroupSettings(
                profileId = profileId,
                scaling =
                    ScalingView(
                        scaling.minOnline,
                        scaling.maxInstances,
                        scaling.playersPerServer,
                        scaling.scaleUpThreshold,
                        scaling.scaleDownThreshold,
                        scaling.cooldown.seconds,
                    ),
                drainTimeoutSeconds = drainTimeout.seconds,
                maintenance = maintenance,
                resources =
                    ResourcesView(
                        resources.memoryRequest,
                        resources.memoryLimit,
                        resources.cpuRequest,
                        resources.cpuLimit,
                    ),
                jvmFlags = jvmFlags,
                image = imageOverride?.let { ImageView(it.image, it.pullSecretRef) },
                storageSize = storageSize,
            ),
        activeTemplateVersionId = activeTemplateVersionId,
        version = version,
    )
