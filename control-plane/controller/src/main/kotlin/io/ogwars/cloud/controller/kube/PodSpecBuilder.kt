package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.domain.group.Group
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.runtime.ProfileRole
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.server.Server
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.ResourceRequirements
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder
import io.fabric8.kubernetes.api.model.SecurityContext
import io.fabric8.kubernetes.api.model.SecurityContextBuilder
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeBuilder
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.api.model.VolumeMountBuilder

/**
 * Turns a server into its pod: pure, so every field is covered by snapshot tests. Game pods hold no infrastructure
 * credentials, only a gateway token bound to the pod (D-0013) and the forwarding tokens (D-0065).
 */
class PodSpecBuilder(
    private val settings: PodProperties,
) {
    fun pod(
        server: Server,
        group: Group,
        profile: RuntimeProfile,
        defaultGroupId: GroupId,
    ): Pod {
        require(server.groupId == group.id) { "Server ${server.id} belongs to ${server.groupId}, not ${group.id}" }
        require(server.profileId == profile.id) { "Server ${server.id} runs ${server.profileId}, not ${profile.id}" }
        val pool = settings.gamePool
        return PodBuilder()
            .withNewMetadata()
            .withName(server.podName)
            .withNamespace(settings.namespace)
            .withLabels<String, String>(labels(server, profile))
            // Empty when created; the lifecycle service flips it while players are online (D-0047).
            .withAnnotations<String, String>(mapOf(PodLabels.SAFE_TO_EVICT to (server.players == 0).toString()))
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("Never")
            .withServiceAccountName(settings.serviceAccount)
            .withAutomountServiceAccountToken(false)
            .withEnableServiceLinks(false)
            .withPriorityClassName(priorityClass(group, defaultGroupId))
            .withTerminationGracePeriodSeconds(settings.terminationGracePeriod.seconds)
            .withNewSecurityContext()
            .withRunAsNonRoot(true)
            .withRunAsUser(IMAGE_UID)
            .withRunAsGroup(IMAGE_UID)
            .withFsGroup(IMAGE_UID)
            .withNewSeccompProfile()
            .withType("RuntimeDefault")
            .endSeccompProfile()
            .endSecurityContext()
            .withInitContainers(templateLoader(server, group), templateAgent(server))
            .withContainers(serverContainer(server, group, profile))
            .withVolumes(volumes(group))
            .apply {
                if (pool != null) {
                    addNewToleration()
                        .withKey(
                            pool.taintKey,
                        ).withValue(pool.taintValue)
                        .withEffect("NoSchedule")
                        .endToleration()
                    withNewAffinity()
                        .withNewNodeAffinity()
                        .withNewRequiredDuringSchedulingIgnoredDuringExecution()
                        .addNewNodeSelectorTerm()
                        .addNewMatchExpression()
                        .withKey(pool.nodeLabel)
                        .withOperator("In")
                        .withValues(pool.nodeLabelValue)
                        .endMatchExpression()
                        .endNodeSelectorTerm()
                        .endRequiredDuringSchedulingIgnoredDuringExecution()
                        .endNodeAffinity()
                        .endAffinity()
                }
            }.endSpec()
            .build()
    }

    /** The volume that keeps a STATIC group's files across restarts; one per group. */
    fun staticVolumeClaim(group: Group): PersistentVolumeClaim {
        require(group.type == GroupType.STATIC) { "Only STATIC groups have a volume, not ${group.id}" }
        return PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName(staticClaimName(group.id))
            .withNamespace(settings.namespace)
            .withLabels<String, String>(
                mapOf(PodLabels.GROUP to group.id.value, PodLabels.MANAGED_BY to PodLabels.CONTROLLER_NAME),
            ).endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withStorageClassName(settings.staticStorageClass)
            .withNewResources()
            .withRequests<String, Quantity>(
                mapOf("storage" to Quantity(group.storageSize ?: settings.staticStorageSize)),
            ).endResources()
            .endSpec()
            .build()
    }

    fun image(
        group: Group,
        profile: RuntimeProfile,
    ): String {
        val override =
            group.imageOverride ?: return platformImage(profile.platform.name.lowercase(), "${profile.version}-")
        require(settings.allowedImagePrefixes.any(override.image::startsWith)) {
            "Image ${override.image} of group ${group.id} is not in the allowed image prefixes"
        }
        return override.image
    }

    private fun labels(
        server: Server,
        profile: RuntimeProfile,
    ) = mapOf(
        PodLabels.NAME to PodLabels.SERVER_NAME,
        PodLabels.MANAGED_BY to PodLabels.CONTROLLER_NAME,
        PodLabels.SERVER_ID to server.id.toString(),
        PodLabels.GROUP to server.groupId.value,
        PodLabels.GROUP_TYPE to server.groupType.name.lowercase(),
        PodLabels.PROFILE to server.profileId,
        PodLabels.ROLE to profile.role.name.lowercase(),
    )

    private fun priorityClass(
        group: Group,
        defaultGroupId: GroupId,
    ): String =
        when {
            group.type == GroupType.PROXY -> settings.priorityClasses.proxy
            group.id == defaultGroupId -> settings.priorityClasses.defaultGroup
            else -> settings.priorityClasses.game
        }

    private fun serverContainer(
        server: Server,
        group: Group,
        profile: RuntimeProfile,
    ): Container =
        ContainerBuilder()
            .withName(SERVER_CONTAINER)
            .withImage(image(group, profile))
            .withEnv(identityEnv(server) + EnvVar(ENV_JVM_FLAGS, group.jvmFlags, null))
            .addNewPort()
            .withName(MINECRAFT_PORT_NAME)
            .withContainerPort(MINECRAFT_PORT)
            .withProtocol("TCP")
            .endPort()
            // Readiness only: a bare pod never restarts, and a hung server is caught by its missing heartbeat.
            .withNewReadinessProbe()
            .withNewTcpSocket()
            .withNewPort(MINECRAFT_PORT_NAME)
            .endTcpSocket()
            .withPeriodSeconds(PROBE_PERIOD_SECONDS)
            .endReadinessProbe()
            .withResources(resources(group))
            .withSecurityContext(restricted())
            .withVolumeMounts(
                mount(DATA_VOLUME, DATA_PATH),
                mount(TMP_VOLUME, TMP_PATH),
                mount(GATEWAY_TOKEN_VOLUME, GATEWAY_TOKEN_PATH, readOnly = true),
                mount(FORWARDING_VOLUME, FORWARDING_PATH, readOnly = true),
            ).build()

    private fun templateLoader(
        server: Server,
        group: Group,
    ): Container =
        ContainerBuilder()
            .withName(LOADER_CONTAINER)
            .withImage(platformImage(TEMPLATE_LOADER_IMAGE))
            .withArgs("load")
            .withEnv(
                identityEnv(server) +
                    EnvVar(ENV_TEMPLATE_VERSION, group.activeTemplateVersionId?.toString().orEmpty(), null),
            ).withResources(SIDECAR_RESOURCES)
            .withSecurityContext(restricted())
            .withVolumeMounts(
                mount(DATA_VOLUME, DATA_PATH),
                mount(GATEWAY_TOKEN_VOLUME, GATEWAY_TOKEN_PATH, readOnly = true),
            ).build()

    // A native sidecar (restartPolicy Always) outlives the server container's start, so it can push on shutdown.
    private fun templateAgent(server: Server): Container =
        ContainerBuilder()
            .withName(AGENT_CONTAINER)
            .withImage(platformImage(TEMPLATE_LOADER_IMAGE))
            .withArgs("agent")
            .withRestartPolicy("Always")
            .withEnv(identityEnv(server))
            .withResources(SIDECAR_RESOURCES)
            .withSecurityContext(restricted())
            .withVolumeMounts(
                mount(DATA_VOLUME, DATA_PATH),
                mount(GATEWAY_TOKEN_VOLUME, GATEWAY_TOKEN_PATH, readOnly = true),
            ).build()

    private fun volumes(group: Group): List<Volume> {
        val data =
            if (group.type == GroupType.STATIC) {
                VolumeBuilder()
                    .withName(
                        DATA_VOLUME,
                    ).withNewPersistentVolumeClaim(staticClaimName(group.id), false)
                    .build()
            } else {
                VolumeBuilder()
                    .withName(DATA_VOLUME)
                    .withNewEmptyDir()
                    .endEmptyDir()
                    .build()
            }
        return listOf(
            data,
            VolumeBuilder()
                .withName(TMP_VOLUME)
                .withNewEmptyDir()
                .endEmptyDir()
                .build(),
            VolumeBuilder()
                .withName(GATEWAY_TOKEN_VOLUME)
                .withNewProjected()
                .addNewSource()
                .withNewServiceAccountToken()
                .withAudience(settings.gatewayAudience)
                .withExpirationSeconds(settings.gatewayTokenTtl.seconds)
                .withPath(GATEWAY_TOKEN_FILE)
                .endServiceAccountToken()
                .endSource()
                .endProjected()
                .build(),
            VolumeBuilder()
                .withName(FORWARDING_VOLUME)
                .withNewSecret()
                .withSecretName(settings.forwardingSecret)
                .withDefaultMode(SECRET_FILE_MODE)
                .endSecret()
                .build(),
        )
    }

    private fun identityEnv(server: Server): List<EnvVar> =
        listOf(
            EnvVar(ENV_SERVER_ID, server.id.toString(), null),
            EnvVar(ENV_GROUP_ID, server.groupId.value, null),
            EnvVar(ENV_GATEWAY_ADDRESS, settings.gatewayAddress, null),
            EnvVar(ENV_GATEWAY_TOKEN_FILE, "$GATEWAY_TOKEN_PATH/$GATEWAY_TOKEN_FILE", null),
        )

    private fun resources(group: Group): ResourceRequirements {
        val spec = group.resources
        val limits =
            mapOf("memory" to Quantity(spec.memoryLimit)) +
                spec.cpuLimit?.let { mapOf("cpu" to Quantity(it)) }.orEmpty()
        return ResourceRequirementsBuilder()
            .withRequests<String, Quantity>(
                mapOf(
                    "memory" to Quantity(spec.memoryRequest),
                    "cpu" to Quantity(spec.cpuRequest),
                ),
            ).withLimits<String, Quantity>(limits)
            .build()
    }

    private fun restricted(): SecurityContext =
        SecurityContextBuilder()
            .withAllowPrivilegeEscalation(false)
            .withReadOnlyRootFilesystem(true)
            .withNewCapabilities()
            .withDrop("ALL")
            .endCapabilities()
            .build()

    private fun mount(
        volume: String,
        path: String,
        readOnly: Boolean = false,
    ): VolumeMount =
        VolumeMountBuilder()
            .withName(volume)
            .withMountPath(path)
            .withReadOnly(readOnly)
            .build()

    /** OgCloud images are tagged with the platform version, server images prefixed by their Minecraft version. */
    private fun platformImage(
        name: String,
        tagPrefix: String = "",
    ) = "${settings.imageRegistry}/$name:$tagPrefix${settings.platformVersion}"

    companion object {
        const val SERVER_CONTAINER = "server"
        const val LOADER_CONTAINER = "template-loader"
        const val AGENT_CONTAINER = "template-agent"
        const val MINECRAFT_PORT = 25565
        const val MINECRAFT_PORT_NAME = "minecraft"

        const val DATA_PATH = "/data"
        const val GATEWAY_TOKEN_PATH = "/var/run/secrets/ogcloud/gateway"
        const val GATEWAY_TOKEN_FILE = "token"
        const val FORWARDING_PATH = "/var/run/secrets/ogcloud/forwarding"

        const val ENV_SERVER_ID = "OGCLOUD_SERVER_ID"
        const val ENV_GROUP_ID = "OGCLOUD_GROUP_ID"
        const val ENV_GATEWAY_ADDRESS = "OGCLOUD_GATEWAY_ADDRESS"
        const val ENV_GATEWAY_TOKEN_FILE = "OGCLOUD_GATEWAY_TOKEN_FILE"
        const val ENV_JVM_FLAGS = "OGCLOUD_JVM_FLAGS"
        const val ENV_TEMPLATE_VERSION = "OGCLOUD_TEMPLATE_VERSION_ID"

        /** OgCloud's images run as this user and group (T-0508). */
        const val IMAGE_UID = 1000L

        fun staticClaimName(groupId: GroupId) = "static-$groupId"

        private const val TEMPLATE_LOADER_IMAGE = "template-loader"
        private const val TMP_PATH = "/tmp"
        private const val DATA_VOLUME = "data"
        private const val TMP_VOLUME = "tmp"
        private const val GATEWAY_TOKEN_VOLUME = "gateway-token"
        private const val FORWARDING_VOLUME = "forwarding"
        private const val PROBE_PERIOD_SECONDS = 5
        private const val SECRET_FILE_MODE = 0b100_100_000 // 0440: readable by the server's group only

        private val SIDECAR_RESOURCES =
            ResourceRequirementsBuilder()
                .withRequests<String, Quantity>(mapOf("memory" to Quantity("64Mi"), "cpu" to Quantity("50m")))
                .withLimits<String, Quantity>(mapOf("memory" to Quantity("128Mi")))
                .build()
    }
}
