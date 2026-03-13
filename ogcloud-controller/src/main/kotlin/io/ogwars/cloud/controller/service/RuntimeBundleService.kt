package io.ogwars.cloud.controller.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.RuntimeBundleScope
import io.ogwars.cloud.common.model.ServerState
import io.ogwars.cloud.controller.config.RuntimeProperties
import io.ogwars.cloud.controller.model.GroupDocument
import io.ogwars.cloud.controller.model.RuntimeArtifactHashDocument
import io.ogwars.cloud.controller.model.resolvedRuntimeProfile
import io.ogwars.cloud.controller.redis.ServerRedisRepository
import io.ogwars.cloud.controller.repository.GroupRepository
import io.ogwars.cloud.controller.repository.RuntimeArtifactHashRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant

@Service
class RuntimeBundleService(
    private val minioClient: MinioClient,
    private val runtimeArtifactHashRepository: RuntimeArtifactHashRepository,
    private val groupRepository: GroupRepository,
    private val serverRedisRepository: ServerRedisRepository,
    private val serverLifecycleService: ServerLifecycleService,
    private val autoscalerService: AutoscalerService,
    private val runtimeProperties: RuntimeProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    fun bootstrapAll() {
        RuntimeBundleScope.entries.forEach { scope ->
            val result = synchronizeScope(scope, requestedBy = "controller-startup", restartAffectedGroups = false)
            log.info(
                "Runtime scope synchronized on startup: scope={}, updatedArtifacts={}, removedArtifacts={}, restartRequired={}",
                scope,
                result.updatedArtifacts,
                result.removedArtifacts,
                result.restartRequired,
            )
        }
    }

    fun refreshScope(
        scope: RuntimeBundleScope,
        requestedBy: String? = null,
    ) {
        val result = synchronizeScope(scope, requestedBy = requestedBy, restartAffectedGroups = true)

        log.info(
            "Runtime refresh processed: scope={}, updatedArtifacts={}, removedArtifacts={}, restartRequired={}, requestedBy={}",
            scope,
            result.updatedArtifacts,
            result.removedArtifacts,
            result.restartRequired,
            requestedBy,
        )
    }

    private fun synchronizeScope(
        scope: RuntimeBundleScope,
        requestedBy: String?,
        restartAffectedGroups: Boolean,
    ): SyncResult {
        ensureRuntimeBucketExists()

        val definitions = resolveArtifactDefinitions(scope)
        val existingByObjectKey = runtimeArtifactHashRepository.findByScope(scope).associateBy(RuntimeArtifactHashDocument::objectKey)
        val activeObjectKeys = definitions.map(RuntimeArtifactDefinition::objectKey).toSet()

        var updatedArtifacts = 0
        var removedArtifacts = 0
        var restartRequired = false
        val manifestArtifacts = mutableListOf<RuntimeManifestArtifact>()

        definitions.forEach { definition ->
            val downloaded = downloadArtifact(definition)
            try {
                val existing = existingByObjectKey[definition.objectKey]
                val objectExists = objectExists(definition.objectKey)
                val contentChanged = existing != null && existing.sha256 != downloaded.sha256
                val metadataChanged =
                    existing == null ||
                        existing.sourceUrl != downloaded.sourceUrl ||
                        existing.upstreamVersion != downloaded.upstreamVersion ||
                        existing.upstreamBuild != downloaded.upstreamBuild ||
                        existing.sizeBytes != downloaded.sizeBytes ||
                        existing.sha256 != downloaded.sha256

                if (existing == null || contentChanged || !objectExists) {
                    uploadObject(definition.objectKey, downloaded.localPath, downloaded.sizeBytes, JAR_CONTENT_TYPE)
                    updatedArtifacts++
                }

                if (metadataChanged) {
                    runtimeArtifactHashRepository.save(
                        RuntimeArtifactHashDocument(
                            id = runtimeArtifactId(scope, definition.objectKey),
                            scope = scope,
                            objectKey = definition.objectKey,
                            sourceUrl = downloaded.sourceUrl,
                            upstreamVersion = downloaded.upstreamVersion,
                            upstreamBuild = downloaded.upstreamBuild,
                            sha256 = downloaded.sha256,
                            sizeBytes = downloaded.sizeBytes,
                            updatedAt = Instant.now(),
                        ),
                    )
                }

                if (contentChanged) {
                    restartRequired = true
                }

                manifestArtifacts +=
                    RuntimeManifestArtifact(
                        objectKey = definition.objectKey,
                        sourceUrl = downloaded.sourceUrl,
                        upstreamVersion = downloaded.upstreamVersion,
                        upstreamBuild = downloaded.upstreamBuild,
                        sha256 = downloaded.sha256,
                        sizeBytes = downloaded.sizeBytes,
                    )
            } finally {
                Files.deleteIfExists(downloaded.localPath)
            }
        }

        existingByObjectKey
            .filterKeys { it !in activeObjectKeys }
            .values
            .forEach { staleArtifact ->
                deleteObject(staleArtifact.objectKey)
                runtimeArtifactHashRepository.delete(staleArtifact)
                removedArtifacts++
                restartRequired = true
            }

        uploadManifest(scope, requestedBy, manifestArtifacts)

        if (restartAffectedGroups && restartRequired) {
            restartGroupsForScope(scope)
        }

        return SyncResult(
            updatedArtifacts = updatedArtifacts,
            removedArtifacts = removedArtifacts,
            restartRequired = restartRequired,
        )
    }

    private fun restartGroupsForScope(scope: RuntimeBundleScope) {
        val groups = groupRepository.findAll().filter { it.matchesRuntimeScope(scope) }

        groups.forEach { group ->
            serverRedisRepository
                .findByGroup(group.id)
                .filterNot { server -> server.state == ServerState.STOPPED }
                .forEach { server ->
                    serverLifecycleService.gracefulStop(server.id, "runtime-refresh-${scope.name.lowercase()}")
                }

            autoscalerService.evaluateGroupNow(group.id)
        }

        log.info("Requested runtime-scope restarts: scope={}, affectedGroups={}", scope, groups.map(GroupDocument::id))
    }

    private fun GroupDocument.matchesRuntimeScope(scope: RuntimeBundleScope): Boolean =
        when (scope) {
            RuntimeBundleScope.VELOCITY -> type == GroupType.PROXY
            else -> type != GroupType.PROXY && resolvedRuntimeProfile()?.runtimeScope == scope
        }

    private fun uploadManifest(
        scope: RuntimeBundleScope,
        requestedBy: String?,
        artifacts: List<RuntimeManifestArtifact>,
    ) {
        val payload =
            objectMapper.writeValueAsBytes(
                RuntimeManifest(
                    scope = scope,
                    requestedBy = requestedBy,
                    generatedAt = Instant.now(),
                    artifacts = artifacts.sortedBy(RuntimeManifestArtifact::objectKey),
                ),
            )

        minioClient.putObject(
            PutObjectArgs
                .builder()
                .bucket(runtimeProperties.bucket)
                .`object`(manifestObjectKey(scope))
                .stream(ByteArrayInputStream(payload), payload.size.toLong(), -1)
                .contentType(JSON_CONTENT_TYPE)
                .build(),
        )
    }

    private fun resolveArtifactDefinitions(scope: RuntimeBundleScope): List<RuntimeArtifactDefinition> =
        when (scope) {
            RuntimeBundleScope.PAPER_1_21_11 ->
                buildList {
                    add(
                        resolvePaperArtifact(
                            scope = scope,
                            version = runtimeProperties.modernPaperVersion,
                            buildId = null,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, OGCLOUD_PAPER_PLUGIN_FILE_NAME),
                            sourceUrl = runtimeProperties.modernPaperPluginUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, BUNGEE_GUARD_FILE_NAME),
                            sourceUrl = runtimeProperties.bungeeGuardUrl,
                        ),
                    )
                }

            RuntimeBundleScope.PAPER_1_8_8 ->
                buildList {
                    add(
                        resolvePaperArtifact(
                            scope = scope,
                            version = runtimeProperties.legacyPaperVersion,
                            buildId = runtimeProperties.legacyPaperBuild,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, OGCLOUD_PAPER_PLUGIN_FILE_NAME),
                            sourceUrl = runtimeProperties.legacyPaperPluginUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, BUNGEE_GUARD_FILE_NAME),
                            sourceUrl = runtimeProperties.bungeeGuardUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, PROTOCOL_LIB_FILE_NAME),
                            sourceUrl = runtimeProperties.protocolLibUrl,
                        ),
                    )
                }

            RuntimeBundleScope.VELOCITY ->
                buildList {
                    add(resolveVelocityArtifact(scope))
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, OGCLOUD_VELOCITY_PLUGIN_FILE_NAME),
                            sourceUrl = runtimeProperties.velocityPluginUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, VIA_VERSION_FILE_NAME),
                            sourceUrl = runtimeProperties.viaVersionUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, VIA_BACKWARDS_FILE_NAME),
                            sourceUrl = runtimeProperties.viaBackwardsUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            scope = scope,
                            objectKey = pluginObjectKey(scope, VIA_REWIND_FILE_NAME),
                            sourceUrl = runtimeProperties.viaRewindUrl,
                        ),
                    )
                }
        }

    private fun resolvePaperArtifact(
        scope: RuntimeBundleScope,
        version: String,
        buildId: Int?,
    ): RuntimeArtifactDefinition {
        val download =
            resolveStableBuildDownload(
                project = runtimeProperties.paperProject,
                version = version,
                buildId = buildId,
                preferredDownloadKey = PAPER_DOWNLOAD_KEY,
            )

        return RuntimeArtifactDefinition(
            objectKey = serverJarObjectKey(scope),
            sourceUrl = download.url,
            upstreamVersion = download.version,
            upstreamBuild = download.buildId,
        )
    }

    private fun resolveVelocityArtifact(scope: RuntimeBundleScope): RuntimeArtifactDefinition {
        val version = latestProjectVersion(runtimeProperties.velocityProject)
        val download =
            resolveStableBuildDownload(
                project = runtimeProperties.velocityProject,
                version = version,
                buildId = null,
                preferredDownloadKey = null,
            )

        return RuntimeArtifactDefinition(
            objectKey = proxyJarObjectKey(scope),
            sourceUrl = download.url,
            upstreamVersion = download.version,
            upstreamBuild = download.buildId,
        )
    }

    private fun managedArtifact(
        scope: RuntimeBundleScope,
        objectKey: String,
        sourceUrl: String,
    ): RuntimeArtifactDefinition =
        RuntimeArtifactDefinition(
            objectKey = objectKey,
            sourceUrl = sourceUrl,
            upstreamVersion = MANAGED_ASSET_VERSION,
            upstreamBuild = MANAGED_ASSET_BUILD,
        )

    private fun resolveStableBuildDownload(
        project: String,
        version: String,
        buildId: Int?,
        preferredDownloadKey: String?,
    ): ResolvedDownload {
        val buildsResponse = getJson("${runtimeProperties.fillBaseUrl}/projects/$project/versions/$version/builds")
        val build =
            buildsResponse
                .asSequence()
                .filter { node -> node.path("channel").asText() == STABLE_CHANNEL }
                .firstOrNull { node -> buildId == null || node.path("id").asInt() == buildId }
                ?: throw IllegalStateException("No stable build found for project=$project version=$version buildId=$buildId")

        val downloadNode = selectDownloadNode(build.path("downloads"), preferredDownloadKey)

        return ResolvedDownload(
            version = version,
            buildId = build.path("id").asInt(),
            url = downloadNode.path("url").asText(),
        )
    }

    private fun selectDownloadNode(
        downloadsNode: JsonNode,
        preferredDownloadKey: String?,
    ): JsonNode {
        if (preferredDownloadKey != null && downloadsNode.has(preferredDownloadKey)) {
            return downloadsNode.path(preferredDownloadKey)
        }

        downloadsNode
            .fields()
            .asSequence()
            .firstOrNull { entry -> entry.key.endsWith(DEFAULT_DOWNLOAD_SUFFIX) }
            ?.let { return it.value }

        return downloadsNode.fields().asSequence().firstOrNull()?.value
            ?: throw IllegalStateException("No downloadable artifact exposed by upstream response")
    }

    private fun latestProjectVersion(project: String): String {
        val projectResponse = getJson("${runtimeProperties.fillBaseUrl}/projects/$project")

        return projectResponse
            .path("versions")
            .fields()
            .asSequence()
            .flatMap { entry -> entry.value.asSequence() }
            .map(JsonNode::asText)
            .firstOrNull()
            ?: throw IllegalStateException("No versions returned for upstream project=$project")
    }

    private fun getJson(url: String): JsonNode {
        val response = sendRequest(url, HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        val root = objectMapper.readTree(body)

        if (response.statusCode() !in SUCCESS_STATUS_RANGE) {
            throw IllegalStateException("Upstream request failed: url=$url status=${response.statusCode()} body=$body")
        }

        if (root.isObject && root.path("ok").isBoolean && !root.path("ok").asBoolean()) {
            throw IllegalStateException("Upstream request failed: url=$url message=${root.path("message").asText("unknown")}")
        }

        return root
    }

    private fun downloadArtifact(definition: RuntimeArtifactDefinition): DownloadedArtifact {
        val response = sendRequest(definition.sourceUrl, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in SUCCESS_STATUS_RANGE) {
            response.body().close()
            throw IllegalStateException(
                "Runtime artifact download failed: url=${definition.sourceUrl} status=${response.statusCode()}",
            )
        }

        val digest = MessageDigest.getInstance(SHA_256)
        val localPath = Files.createTempFile("ogcloud-runtime-", ".jar")

        response.body().use { input ->
            DigestInputStream(input, digest).use { digestedInput ->
                Files.newOutputStream(localPath).use { output ->
                    digestedInput.transferTo(output)
                }
            }
        }

        return DownloadedArtifact(
            objectKey = definition.objectKey,
            sourceUrl = definition.sourceUrl,
            upstreamVersion = definition.upstreamVersion,
            upstreamBuild = definition.upstreamBuild,
            localPath = localPath,
            sha256 = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) },
            sizeBytes = Files.size(localPath),
        )
    }

    private fun uploadObject(
        objectKey: String,
        localPath: Path,
        sizeBytes: Long,
        contentType: String,
    ) {
        Files.newInputStream(localPath).use { input ->
            minioClient.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(runtimeProperties.bucket)
                    .`object`(objectKey)
                    .stream(input, sizeBytes, -1)
                    .contentType(contentType)
                    .build(),
            )
        }
    }

    private fun deleteObject(objectKey: String) {
        minioClient.removeObject(
            RemoveObjectArgs
                .builder()
                .bucket(runtimeProperties.bucket)
                .`object`(objectKey)
                .build(),
        )
    }

    private fun objectExists(objectKey: String): Boolean =
        try {
            minioClient.statObject(
                StatObjectArgs
                    .builder()
                    .bucket(runtimeProperties.bucket)
                    .`object`(objectKey)
                    .build(),
            )
            true
        } catch (_: Exception) {
            false
        }

    private fun ensureRuntimeBucketExists() {
        val bucketExists =
            minioClient.bucketExists(
                BucketExistsArgs
                    .builder()
                    .bucket(runtimeProperties.bucket)
                    .build(),
            )

        if (!bucketExists) {
            minioClient.makeBucket(
                MakeBucketArgs
                    .builder()
                    .bucket(runtimeProperties.bucket)
                    .build(),
            )
        }
    }

    private fun <T> sendRequest(
        url: String,
        bodyHandler: HttpResponse.BodyHandler<T>,
    ): HttpResponse<T> {
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .header(USER_AGENT_HEADER, runtimeProperties.userAgent)
                .GET()
                .build()

        return httpClient.send(request, bodyHandler)
    }

    private fun manifestObjectKey(scope: RuntimeBundleScope): String = "${scope.minioPrefix}/manifest.json"

    private fun serverJarObjectKey(scope: RuntimeBundleScope): String = "${scope.minioPrefix}/server.jar"

    private fun proxyJarObjectKey(scope: RuntimeBundleScope): String = "${scope.minioPrefix}/proxy.jar"

    private fun pluginObjectKey(
        scope: RuntimeBundleScope,
        fileName: String,
    ): String = "${scope.minioPrefix}/plugins/$fileName"

    private fun runtimeArtifactId(
        scope: RuntimeBundleScope,
        objectKey: String,
    ): String = "${scope.name}:$objectKey"

    private data class RuntimeArtifactDefinition(
        val objectKey: String,
        val sourceUrl: String,
        val upstreamVersion: String,
        val upstreamBuild: Int,
    )

    private data class DownloadedArtifact(
        val objectKey: String,
        val sourceUrl: String,
        val upstreamVersion: String,
        val upstreamBuild: Int,
        val localPath: Path,
        val sha256: String,
        val sizeBytes: Long,
    )

    private data class ResolvedDownload(
        val version: String,
        val buildId: Int,
        val url: String,
    )

    private data class SyncResult(
        val updatedArtifacts: Int,
        val removedArtifacts: Int,
        val restartRequired: Boolean,
    )

    private data class RuntimeManifest(
        val scope: RuntimeBundleScope,
        val requestedBy: String?,
        val generatedAt: Instant,
        val artifacts: List<RuntimeManifestArtifact>,
    )

    private data class RuntimeManifestArtifact(
        val objectKey: String,
        val sourceUrl: String,
        val upstreamVersion: String,
        val upstreamBuild: Int,
        val sha256: String,
        val sizeBytes: Long,
    )

    companion object {
        private const val OGCLOUD_PAPER_PLUGIN_FILE_NAME = "ogcloud-paper-plugin.jar"
        private const val OGCLOUD_VELOCITY_PLUGIN_FILE_NAME = "ogcloud-velocity-plugin.jar"
        private const val VIA_VERSION_FILE_NAME = "ViaVersion.jar"
        private const val VIA_BACKWARDS_FILE_NAME = "ViaBackwards.jar"
        private const val VIA_REWIND_FILE_NAME = "ViaRewind.jar"
        private const val BUNGEE_GUARD_FILE_NAME = "BungeeGuard.jar"
        private const val PROTOCOL_LIB_FILE_NAME = "ProtocolLib.jar"
        private const val MANAGED_ASSET_VERSION = "managed"
        private const val MANAGED_ASSET_BUILD = 0
        private const val PAPER_DOWNLOAD_KEY = "server:default"
        private const val STABLE_CHANNEL = "STABLE"
        private const val DEFAULT_DOWNLOAD_SUFFIX = ":default"
        private const val USER_AGENT_HEADER = "User-Agent"
        private const val SHA_256 = "SHA-256"
        private const val JAR_CONTENT_TYPE = "application/java-archive"
        private const val JSON_CONTENT_TYPE = "application/json"
        private val SUCCESS_STATUS_RANGE = 200..299
    }
}
