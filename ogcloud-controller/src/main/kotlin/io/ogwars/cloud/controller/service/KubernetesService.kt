package io.ogwars.cloud.controller.service

import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.RuntimeBundleScope
import io.ogwars.cloud.controller.config.KubernetesProperties
import io.ogwars.cloud.controller.config.PodRuntimeProperties
import io.ogwars.cloud.controller.config.RuntimeProperties
import io.ogwars.cloud.controller.model.GroupDocument
import io.ogwars.cloud.controller.model.ServerDocument
import io.ogwars.cloud.controller.model.resolvedRuntimeProfile
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class KubernetesService(
    private val kubernetesClient: KubernetesClient,
    private val networkSettingsService: NetworkSettingsService,
    private val kubernetesProperties: KubernetesProperties,
    private val podRuntimeProperties: PodRuntimeProperties,
    private val runtimeProperties: RuntimeProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createServerPod(
        server: ServerDocument,
        group: GroupDocument,
    ) {
        val podSpec = group.toServerPodSpec()

        if (podSpec.isStatic) {
            createPvcIfNotExists(group.id, group.storageSize)
        }

        val volume = buildServerDataVolume(group.id, podSpec.isStatic)

        val pod =
            PodBuilder()
                .withNewMetadata()
                .withName(server.podName)
                .withNamespace(kubernetesProperties.namespace)
                .addToLabels("ogcloud/component", if (podSpec.isProxy) PROXY_COMPONENT else SERVER_COMPONENT)
                .addToLabels("ogcloud/group", server.group)
                .addToLabels("ogcloud/group-type", group.type.name.lowercase())
                .addToLabels("ogcloud/server-id", server.id)
                .endMetadata()
                .withNewSpec()
                .withTerminationGracePeriodSeconds(podSpec.terminationGraceSeconds)
                .withNewSecurityContext()
                .withRunAsUser(1000L)
                .withRunAsGroup(1000L)
                .withFsGroup(1000L)
                .endSecurityContext()
                .addNewInitContainer()
                .withName(TEMPLATE_LOADER_NAME)
                .withImage(templateLoaderImage())
                .addAllToEnv(
                    buildTemplateLoaderEnvVars(
                        templateBucket = group.templateBucket,
                        templatePath = podSpec.templatePath,
                        runtimeBucket = runtimeProperties.bucket,
                        runtimeManifestPath = runtimeManifestPath(group),
                        forwardingSecret = podRuntimeProperties.forwardingSecret,
                    ),
                ).addNewVolumeMount()
                .withName(SERVER_DATA_VOLUME_NAME)
                .withMountPath(DATA_DIR)
                .endVolumeMount()
                .endInitContainer()
                .addNewContainer()
                .withName(if (podSpec.isProxy) PROXY_CONTAINER_NAME else SERVER_CONTAINER_NAME)
                .withImage(group.serverImage)
                .addAllToEnv(buildRuntimeEnvVars(server, group, podSpec.isProxy))
                .addNewPort()
                .withContainerPort(podSpec.containerPort)
                .endPort()
                .withNewResources()
                .addToRequests("memory", Quantity(group.resources.memoryRequest))
                .addToRequests("cpu", Quantity(group.resources.cpuRequest))
                .addToLimits("memory", Quantity(group.resources.memoryLimit))
                .addToLimits("cpu", Quantity(group.resources.cpuLimit))
                .endResources()
                .addNewVolumeMount()
                .withName(SERVER_DATA_VOLUME_NAME)
                .withMountPath(DATA_DIR)
                .endVolumeMount()
                .withNewReadinessProbe()
                .withNewTcpSocket()
                .withNewPort(podSpec.containerPort)
                .endTcpSocket()
                .withInitialDelaySeconds(15)
                .withPeriodSeconds(5)
                .endReadinessProbe()
                .endContainer()
                .apply {
                    if (!podSpec.isProxy) {
                        addNewContainer()
                            .withName("template-pusher")
                            .withImage(templateLoaderImage())
                            .addAllToEnv(
                                buildTemplatePusherEnvVars(
                                    group.templateBucket,
                                    podSpec.templatePath,
                                    podSpec.isStatic,
                                ),
                            ).addNewVolumeMount()
                            .withName(SERVER_DATA_VOLUME_NAME)
                            .withMountPath(DATA_DIR)
                            .endVolumeMount()
                            .endContainer()
                    }
                }.addNewVolume()
                .withName(volume.name)
                .withPersistentVolumeClaim(volume.persistentVolumeClaim)
                .withEmptyDir(volume.emptyDir)
                .endVolume()
                .endSpec()
                .build()

        kubernetesClient
            .pods()
            .inNamespace(kubernetesProperties.namespace)
            .resource(pod)
            .create()

        log.info(
            "Created pod: name={}, namespace={}, type={}, static={}",
            server.podName,
            kubernetesProperties.namespace,
            group.type,
            podSpec.isStatic,
        )
    }

    private fun createPvcIfNotExists(
        groupId: String,
        storageSize: String,
    ) {
        val pvcName = staticStorageClaimName(groupId)
        val existing =
            kubernetesClient
                .persistentVolumeClaims()
                .inNamespace(kubernetesProperties.namespace)
                .withName(pvcName)
                .get()

        if (existing != null) {
            log.info("PVC already exists: name={}", pvcName)
            return
        }

        val pvc =
            PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(pvcName)
                .withNamespace(kubernetesProperties.namespace)
                .addToLabels("ogcloud/component", "static-storage")
                .addToLabels("ogcloud/group", groupId)
                .endMetadata()
                .withNewSpec()
                .addToAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", Quantity(storageSize))
                .endResources()
                .endSpec()
                .build()

        kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(kubernetesProperties.namespace)
            .resource(pvc)
            .create()

        log.info("Created PVC: name={}, size={}", pvcName, storageSize)
    }

    fun execInContainer(
        podName: String,
        containerName: String,
        command: List<String>,
    ): String {
        val output = ByteArrayOutputStream()
        val errorOutput = ByteArrayOutputStream()

        kubernetesClient
            .pods()
            .inNamespace(kubernetesProperties.namespace)
            .withName(podName)
            .inContainer(containerName)
            .writingOutput(output)
            .writingError(errorOutput)
            .exec(*command.toTypedArray())

        val result = output.toString()
        val error = errorOutput.toString()

        if (error.isNotBlank()) {
            log.warn("Exec error in pod={}, container={}: {}", podName, containerName, error)
        }

        return result
    }

    private fun buildRuntimeEnvVars(
        server: ServerDocument,
        group: GroupDocument,
        isProxy: Boolean,
    ): List<EnvVar> =
        if (isProxy) {
            buildProxyEnvVars(server, group)
        } else {
            buildServerEnvVars(server, group)
        }

    private fun buildServerEnvVars(
        server: ServerDocument,
        group: GroupDocument,
    ): List<EnvVar> =
        listOf(
            envVar("EULA", "TRUE"),
            envVar("TYPE", "PAPER"),
            envVar("VERSION", requireNotNull(group.resolvedRuntimeProfile()).minecraftVersion),
            envVar("OGCLOUD_SERVER_ID", server.id),
            envVar("OGCLOUD_GROUP", server.group),
            podIpEnvVar("OGCLOUD_SERVER_POD_IP"),
            envVar("OGCLOUD_MAX_PLAYERS", group.scaling.playersPerServer.toString()),
            envVar("OGCLOUD_API_URL", podRuntimeProperties.apiUrl),
            envVar("OGCLOUD_API_EMAIL", podRuntimeProperties.apiEmail),
            envVar("OGCLOUD_API_PASSWORD", podRuntimeProperties.apiPassword),
            envVar("KAFKA_BROKERS", podRuntimeProperties.kafkaBrokers),
            envVar(
                "OGCLOUD_KAFKA_CONSUMER_RESTART_INITIAL_BACKOFF_MS",
                podRuntimeProperties.kafkaConsumerRestartInitialBackoffMs,
            ),
            envVar(
                "OGCLOUD_KAFKA_CONSUMER_RESTART_MAX_BACKOFF_MS",
                podRuntimeProperties.kafkaConsumerRestartMaxBackoffMs,
            ),
            envVar(
                "OGCLOUD_KAFKA_CONSUMER_RESTART_JITTER_MS",
                podRuntimeProperties.kafkaConsumerRestartJitterMs,
            ),
            envVar("REDIS_HOST", podRuntimeProperties.redisHost),
            envVar("REDIS_PORT", podRuntimeProperties.redisPort),
            envVar("MONGODB_URI", podRuntimeProperties.mongodbUri),
            envVar("JVM_FLAGS", group.jvmFlags),
        )

    private fun buildProxyEnvVars(
        server: ServerDocument,
        group: GroupDocument,
    ): List<EnvVar> {
        val defaultGroup = networkSettingsService.findGlobal().defaultGroup

        return listOf(
            envVar("OGCLOUD_PROXY_ID", server.id),
            envVar("OGCLOUD_PROXY_DISPLAY_NAME", server.displayName),
            envVar("OGCLOUD_GROUP", server.group),
            envVar("OGCLOUD_MAX_PLAYERS", group.scaling.playersPerServer.toString()),
            podIpEnvVar("OGCLOUD_PROXY_POD_IP"),
            envVar("OGCLOUD_PROXY_PORT", PROXY_PORT.toString()),
            envVar("OGCLOUD_DEFAULT_GROUP", defaultGroup),
            envVar("KAFKA_BROKERS", podRuntimeProperties.kafkaBrokers),
            envVar(
                "OGCLOUD_KAFKA_CONSUMER_RESTART_INITIAL_BACKOFF_MS",
                podRuntimeProperties.kafkaConsumerRestartInitialBackoffMs,
            ),
            envVar(
                "OGCLOUD_KAFKA_CONSUMER_RESTART_MAX_BACKOFF_MS",
                podRuntimeProperties.kafkaConsumerRestartMaxBackoffMs,
            ),
            envVar(
                "OGCLOUD_KAFKA_CONSUMER_RESTART_JITTER_MS",
                podRuntimeProperties.kafkaConsumerRestartJitterMs,
            ),
            envVar("REDIS_HOST", podRuntimeProperties.redisHost),
            envVar("REDIS_PORT", podRuntimeProperties.redisPort),
            envVar("MONGODB_URI", podRuntimeProperties.mongodbUri),
            envVar("OGCLOUD_API_URL", podRuntimeProperties.apiUrl),
            envVar("OGCLOUD_API_EMAIL", podRuntimeProperties.apiEmail),
            envVar("OGCLOUD_API_PASSWORD", podRuntimeProperties.apiPassword),
            envVar("JVM_FLAGS", group.jvmFlags),
        )
    }

    private fun buildTemplateLoaderEnvVars(
        templateBucket: String,
        templatePath: String,
        runtimeBucket: String,
        runtimeManifestPath: String,
        forwardingSecret: String,
    ): List<EnvVar> =
        listOf(
            envVar("MINIO_ENDPOINT", podRuntimeProperties.minioEndpoint),
            envVar("MINIO_ACCESS_KEY", podRuntimeProperties.minioAccessKey),
            envVar("MINIO_SECRET_KEY", podRuntimeProperties.minioSecretKey),
            envVar("MINIO_BUCKET", templateBucket),
            envVar("TEMPLATE_PATH", templatePath),
            envVar("RUNTIME_BUCKET", runtimeBucket),
            envVar("RUNTIME_MANIFEST_PATH", runtimeManifestPath),
            envVar("FORWARDING_SECRET", forwardingSecret),
            envVar("DATA_DIR", DATA_DIR),
        )

    private fun buildTemplatePusherEnvVars(
        bucket: String,
        templatePath: String,
        pushOnShutdown: Boolean,
    ): List<EnvVar> =
        listOf(
            envVar("MODE", PUSH_MODE),
            *buildTemplateLoaderEnvVars(
                templateBucket = bucket,
                templatePath = templatePath,
                runtimeBucket = runtimeProperties.bucket,
                runtimeManifestPath = "",
                forwardingSecret = "",
            ).toTypedArray(),
            envVar("PUSH_ON_SHUTDOWN", if (pushOnShutdown) ENABLED_VALUE else DISABLED_VALUE),
        )

    private fun buildServerDataVolume(
        groupId: String,
        isStatic: Boolean,
    ): Volume =
        if (isStatic) {
            VolumeBuilder()
                .withName(SERVER_DATA_VOLUME_NAME)
                .withNewPersistentVolumeClaim(staticStorageClaimName(groupId), false)
                .build()
        } else {
            VolumeBuilder()
                .withName(SERVER_DATA_VOLUME_NAME)
                .withNewEmptyDir()
                .endEmptyDir()
                .build()
        }

    private fun staticStorageClaimName(groupId: String): String = STATIC_STORAGE_CLAIM_PREFIX + groupId

    private fun runtimeManifestPath(group: GroupDocument): String {
        val scope =
            if (group.type == GroupType.PROXY) {
                RuntimeBundleScope.VELOCITY
            } else {
                requireNotNull(group.resolvedRuntimeProfile()).runtimeScope
            }

        return "${scope.minioPrefix}/manifest.json"
    }

    private fun envVar(
        name: String,
        value: String,
    ): EnvVar = EnvVarBuilder().withName(name).withValue(value).build()

    private fun podIpEnvVar(name: String): EnvVar =
        EnvVarBuilder()
            .withName(name)
            .withNewValueFrom()
            .withNewFieldRef()
            .withFieldPath("status.podIP")
            .endFieldRef()
            .endValueFrom()
            .build()

    fun deleteServerPod(podName: String) {
        kubernetesClient
            .pods()
            .inNamespace(kubernetesProperties.namespace)
            .withName(podName)
            .delete()

        log.info("Deleted pod: name={}", podName)
    }

    fun forceDeleteServerPod(podName: String) {
        kubernetesClient
            .pods()
            .inNamespace(kubernetesProperties.namespace)
            .withName(podName)
            .withGracePeriod(0)
            .delete()

        log.info("Force-deleted pod: name={}", podName)
    }

    private data class ServerPodSpec(
        val isProxy: Boolean,
        val isStatic: Boolean,
        val templatePath: String,
        val containerPort: Int,
        val terminationGraceSeconds: Long,
    )

    private fun GroupDocument.toServerPodSpec(): ServerPodSpec {
        val isProxy = type == GroupType.PROXY
        val isStatic = type == GroupType.STATIC
        return ServerPodSpec(
            isProxy = isProxy,
            isStatic = isStatic,
            templatePath = "$templatePath/$templateVersion/template.tar.gz",
            containerPort = if (isProxy) PROXY_PORT else PAPER_PORT,
            terminationGraceSeconds =
                if (isStatic) {
                    STATIC_TERMINATION_GRACE_SECONDS
                } else {
                    DEFAULT_TERMINATION_GRACE_SECONDS
                },
        )
    }

    private fun templateLoaderImage(): String {
        val version = runtimeProperties.templateLoaderVersion.trim().ifBlank { DEFAULT_TEMPLATE_LOADER_VERSION }
        return "$TEMPLATE_LOADER_IMAGE_REPOSITORY:$version"
    }

    companion object {
        private const val PAPER_PORT = 25565
        private const val PROXY_PORT = 25577
        private const val DEFAULT_TERMINATION_GRACE_SECONDS = 60L
        private const val STATIC_TERMINATION_GRACE_SECONDS = 120L
        private const val DATA_DIR = "/opt/minecraft/server"
        private const val SERVER_DATA_VOLUME_NAME = "server-data"
        private const val TEMPLATE_LOADER_NAME = "template-loader"
        private const val TEMPLATE_LOADER_IMAGE_REPOSITORY = "ogwarsdev/template-loader"
        private const val DEFAULT_TEMPLATE_LOADER_VERSION = "latest"
        private const val STATIC_STORAGE_CLAIM_PREFIX = "ogcloud-static-"
        private const val PROXY_COMPONENT = "proxy"
        private const val SERVER_COMPONENT = "server"
        private const val PROXY_CONTAINER_NAME = "velocity"
        private const val SERVER_CONTAINER_NAME = "paper"
        private const val PUSH_MODE = "push"
        private const val ENABLED_VALUE = "true"
        private const val DISABLED_VALUE = "false"
    }
}
