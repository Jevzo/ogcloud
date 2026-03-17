package io.ogwars.cloud.controller.service

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
import com.fasterxml.jackson.databind.ObjectMapper
import io.minio.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant

private fun runtimeTempFileSuffix(objectKey: String): String {
    val fileName = objectKey.substringAfterLast('/')
    val extension = fileName.substringAfterLast('.', "")
    return if (extension.isBlank()) {
        ".tmp"
    } else {
        ".$extension"
    }
}

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
        val reusableArtifactCache = mutableMapOf<String, CachedArtifact>()

        try {
            RuntimeBundleScope.entries.forEach { scope ->
                val result =
                    synchronizeScope(
                        scope = scope,
                        requestedBy = "controller-startup",
                        restartAffectedGroups = false,
                        reusableArtifactCache = reusableArtifactCache,
                    )
                log.info(
                    "Runtime scope synchronized on startup: scope={}, updatedArtifacts={}, removedArtifacts={}, restartRequired={}",
                    scope,
                    result.updatedArtifacts,
                    result.removedArtifacts,
                    result.restartRequired,
                )
            }
        } finally {
            reusableArtifactCache.values.forEach { cachedArtifact ->
                Files.deleteIfExists(cachedArtifact.localPath)
            }
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
        reusableArtifactCache: MutableMap<String, CachedArtifact>? = null,
    ): SyncResult {
        ensureRuntimeBucketExists()

        val definitions = resolveArtifactDefinitions(scope)
        val existingByObjectKey =
            runtimeArtifactHashRepository
                .findByScope(
                    scope,
                ).associateBy(RuntimeArtifactHashDocument::objectKey)
        val activeObjectKeys = definitions.map(RuntimeArtifactDefinition::objectKey).toSet()

        var updatedArtifacts = 0
        var removedArtifacts = 0
        var restartRequired = false
        val manifestArtifacts = mutableListOf<RuntimeManifestArtifact>()

        definitions.forEach { definition ->
            val existing = existingByObjectKey[definition.objectKey]
            val objectExists = objectExists(definition.objectKey)

            if (shouldReuseExistingArtifactOnStartup(definition, existing, objectExists, restartAffectedGroups)) {
                existing?.let { existingArtifact ->
                    manifestArtifacts += existingArtifact.toManifestArtifact()
                    log.info(
                        "Reusing existing runtime artifact on startup without redownload: scope={}, objectKey={}",
                        scope,
                        definition.objectKey,
                    )
                }
                return@forEach
            }

            val downloaded = materializeArtifact(definition, reusableArtifactCache)
            try {
                val contentChanged = existing != null && existing.sha256 != downloaded.sha256
                val metadataChanged =
                    existing == null ||
                        existing.sourceUrl != downloaded.sourceUrl ||
                        existing.upstreamVersion != downloaded.upstreamVersion ||
                        existing.upstreamBuild != downloaded.upstreamBuild ||
                        existing.sizeBytes != downloaded.sizeBytes ||
                        existing.sha256 != downloaded.sha256

                if (existing == null || contentChanged || !objectExists) {
                    uploadObject(
                        definition.objectKey,
                        downloaded.localPath,
                        downloaded.sizeBytes,
                        downloaded.contentType,
                    )
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

    private fun shouldReuseExistingArtifactOnStartup(
        definition: RuntimeArtifactDefinition,
        existing: RuntimeArtifactHashDocument?,
        objectExists: Boolean,
        restartAffectedGroups: Boolean,
    ): Boolean = !restartAffectedGroups && definition.inlineContent == null && existing != null && objectExists

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
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, OGCLOUD_PAPER_PLUGIN_FILE_NAME),
                            sourceUrl = runtimeProperties.modernPaperPluginUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, BUNGEE_GUARD_FILE_NAME),
                            sourceUrl = runtimeProperties.bungeeGuardUrl,
                        ),
                    )
                    add(
                        generatedArtifact(
                            scope = scope,
                            objectKey = bungeeGuardConfigObjectKey(scope),
                            sourceId = "bungeeguard-config",
                            content = bungeeGuardConfigSkeleton(),
                        ),
                    )
                }

            RuntimeBundleScope.PAPER_1_8_8 ->
                buildList {
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, OGCLOUD_PAPER_PLUGIN_FILE_NAME),
                            sourceUrl = runtimeProperties.legacyPaperPluginUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, BUNGEE_GUARD_FILE_NAME),
                            sourceUrl = runtimeProperties.bungeeGuardUrl,
                        ),
                    )
                    add(
                        generatedArtifact(
                            scope = scope,
                            objectKey = bungeeGuardConfigObjectKey(scope),
                            sourceId = "bungeeguard-config",
                            content = bungeeGuardConfigSkeleton(),
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, PROTOCOL_LIB_FILE_NAME),
                            sourceUrl = runtimeProperties.protocolLibUrl,
                        ),
                    )
                }

            RuntimeBundleScope.VELOCITY ->
                buildList {
                    add(
                        generatedArtifact(
                            scope = scope,
                            objectKey = velocityConfigObjectKey(scope),
                            sourceId = "velocity-toml",
                            content = velocityToml(),
                        ),
                    )
                    add(
                        generatedArtifact(
                            scope = scope,
                            objectKey = viaVersionConfigObjectKey(scope),
                            sourceId = "viaversion-config-v1",
                            content = viaVersionConfig(),
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, OGCLOUD_VELOCITY_PLUGIN_FILE_NAME),
                            sourceUrl = runtimeProperties.velocityPluginUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, VIA_VERSION_FILE_NAME),
                            sourceUrl = runtimeProperties.viaVersionUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, VIA_BACKWARDS_FILE_NAME),
                            sourceUrl = runtimeProperties.viaBackwardsUrl,
                        ),
                    )
                    add(
                        managedArtifact(
                            objectKey = pluginObjectKey(scope, VIA_REWIND_FILE_NAME),
                            sourceUrl = runtimeProperties.viaRewindUrl,
                        ),
                    )
                }
        }

    private fun managedArtifact(
        objectKey: String,
        sourceUrl: String,
        reusableAcrossScopes: Boolean = false,
    ): RuntimeArtifactDefinition =
        RuntimeArtifactDefinition(
            objectKey = objectKey,
            sourceUrl = sourceUrl,
            upstreamVersion = MANAGED_ASSET_VERSION,
            upstreamBuild = MANAGED_ASSET_BUILD,
            contentType = contentTypeForObjectKey(objectKey),
            reusableAcrossScopes = reusableAcrossScopes,
        )

    private fun generatedArtifact(
        scope: RuntimeBundleScope,
        objectKey: String,
        sourceId: String,
        content: String,
    ): RuntimeArtifactDefinition =
        RuntimeArtifactDefinition(
            objectKey = objectKey,
            sourceUrl = "generated://${scope.name.lowercase()}/$sourceId",
            upstreamVersion = MANAGED_ASSET_VERSION,
            upstreamBuild = MANAGED_ASSET_BUILD,
            contentType = contentTypeForObjectKey(objectKey),
            inlineContent = content,
        )

    private fun materializeArtifact(
        definition: RuntimeArtifactDefinition,
        reusableArtifactCache: MutableMap<String, CachedArtifact>? = null,
    ): DownloadedArtifact =
        definition.inlineContent?.let { inlineContent ->
            materializeInlineArtifact(definition, inlineContent)
        } ?: materializeDownloadedArtifact(definition, reusableArtifactCache)

    private fun materializeDownloadedArtifact(
        definition: RuntimeArtifactDefinition,
        reusableArtifactCache: MutableMap<String, CachedArtifact>? = null,
    ): DownloadedArtifact {
        val cacheKey = definition.cacheKey()
        if (definition.reusableAcrossScopes && reusableArtifactCache != null) {
            reusableArtifactCache[cacheKey]?.let { cachedArtifact ->
                return cachedArtifact.materialize(definition.objectKey)
            }
        }

        val downloaded = downloadArtifact(definition)
        if (definition.reusableAcrossScopes && reusableArtifactCache != null) {
            reusableArtifactCache[cacheKey] = CachedArtifact.from(downloaded)
        }
        return downloaded
    }

    private fun materializeInlineArtifact(
        definition: RuntimeArtifactDefinition,
        inlineContent: String,
    ): DownloadedArtifact {
        val digest = MessageDigest.getInstance(SHA_256)
        val localPath = Files.createTempFile("ogcloud-runtime-", runtimeTempFileSuffix(definition.objectKey))
        val inlineContentBytes = inlineContent.toByteArray(StandardCharsets.UTF_8)
        Files.write(localPath, inlineContentBytes)

        return DownloadedArtifact(
            objectKey = definition.objectKey,
            sourceUrl = definition.sourceUrl,
            upstreamVersion = definition.upstreamVersion,
            upstreamBuild = definition.upstreamBuild,
            localPath = localPath,
            sha256 = digest.digest(inlineContentBytes).joinToString(separator = "") { byte -> "%02x".format(byte) },
            sizeBytes = inlineContentBytes.size.toLong(),
            contentType = definition.contentType,
        )
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
            contentType = definition.contentType,
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

    private fun velocityConfigObjectKey(scope: RuntimeBundleScope): String = "${scope.minioPrefix}/velocity.toml"

    private fun viaVersionConfigObjectKey(scope: RuntimeBundleScope): String =
        "${scope.minioPrefix}/plugins/viaversion/config.yml"

    private fun pluginObjectKey(
        scope: RuntimeBundleScope,
        fileName: String,
    ): String = "${scope.minioPrefix}/plugins/$fileName"

    private fun bungeeGuardConfigObjectKey(scope: RuntimeBundleScope): String =
        "${scope.minioPrefix}/plugins/BungeeGuard/config.yml"

    private fun contentTypeForObjectKey(objectKey: String): String =
        when {
            objectKey.endsWith(".jar") -> JAR_CONTENT_TYPE
            objectKey.endsWith(".json") -> JSON_CONTENT_TYPE
            else -> TEXT_CONTENT_TYPE
        }

    private fun velocityToml(): String =
        """
        config-version = "2.7"
        bind = "0.0.0.0:$DEFAULT_PROXY_PORT"
        motd = "<#09add3>OgCloud Network"
        show-max-players = 500
        online-mode = true
        force-key-authentication = true
        prevent-client-proxy-connections = false
        player-info-forwarding-mode = "bungeeguard"
        forwarding-secret-file = "forwarding.secret"
        announce-forge = false
        kick-existing-players = false
        ping-passthrough = "DISABLED"
        sample-players-in-ping = false
        enable-player-address-logging = true
        
        [servers]
        try = []
        
        [forced-hosts]
        
        [advanced]
        compression-threshold = 256
        compression-level = -1
        login-ratelimit = 3000
        connection-timeout = 5000
        read-timeout = 30000
        haproxy-protocol = true
        tcp-fast-open = false
        bungee-plugin-message-channel = true
        show-ping-requests = false
        failover-on-unexpected-server-disconnect = true
        announce-proxy-commands = true
        log-command-executions = false
        log-player-connections = true
        accepts-transfers = false
        enable-reuse-port = false
        command-rate-limit = 50
        forward-commands-if-rate-limited = true
        kick-after-rate-limited-commands = 0
        tab-complete-rate-limit = 10
        kick-after-rate-limited-tab-completes = 0
        
        [query]
        enabled = false
        port = $DEFAULT_PROXY_PORT
        map = "Velocity"
        show-plugins = false
        """.trimIndent() + "\n"

    private fun bungeeGuardConfigSkeleton(): String = "allowed-tokens: []\n"

    private fun viaVersionConfig(): String =
        """
        check-for-updates: true
        config-version: 1
        init-config-version: 1
        migrate-default-config-changes: true
        send-player-details: true
        send-server-details: true
        block-versions:
          - "<1.8"
        block-disconnect-msg: "&cUnsupported Minecraft version. Minimum supported version is 1.8.8."
        velocity-ping-interval: 60
        velocity-ping-save: true
        velocity-servers:
          default: ${runtimeProperties.viaVersionDefaultServerProtocol}
        """.trimIndent() + "\n"

    private fun runtimeArtifactId(
        scope: RuntimeBundleScope,
        objectKey: String,
    ): String = "${scope.name}:$objectKey"

    private data class RuntimeArtifactDefinition(
        val objectKey: String,
        val sourceUrl: String,
        val upstreamVersion: String,
        val upstreamBuild: Int,
        val contentType: String,
        val inlineContent: String? = null,
        val reusableAcrossScopes: Boolean = false,
    )

    private data class DownloadedArtifact(
        val objectKey: String,
        val sourceUrl: String,
        val upstreamVersion: String,
        val upstreamBuild: Int,
        val localPath: Path,
        val sha256: String,
        val sizeBytes: Long,
        val contentType: String,
    )

    private data class CachedArtifact(
        val sourceUrl: String,
        val upstreamVersion: String,
        val upstreamBuild: Int,
        val localPath: Path,
        val sha256: String,
        val sizeBytes: Long,
        val contentType: String,
    ) {
        fun materialize(objectKey: String): DownloadedArtifact {
            val localCopy = Files.createTempFile("ogcloud-runtime-", runtimeTempFileSuffix(objectKey))
            Files.copy(localPath, localCopy, StandardCopyOption.REPLACE_EXISTING)

            return DownloadedArtifact(
                objectKey = objectKey,
                sourceUrl = sourceUrl,
                upstreamVersion = upstreamVersion,
                upstreamBuild = upstreamBuild,
                localPath = localCopy,
                sha256 = sha256,
                sizeBytes = sizeBytes,
                contentType = contentType,
            )
        }

        companion object {
            fun from(downloadedArtifact: DownloadedArtifact): CachedArtifact {
                val cachedPath =
                    Files.createTempFile(
                        "ogcloud-runtime-cache-",
                        runtimeTempFileSuffix(downloadedArtifact.objectKey),
                    )
                Files.copy(downloadedArtifact.localPath, cachedPath, StandardCopyOption.REPLACE_EXISTING)

                return CachedArtifact(
                    sourceUrl = downloadedArtifact.sourceUrl,
                    upstreamVersion = downloadedArtifact.upstreamVersion,
                    upstreamBuild = downloadedArtifact.upstreamBuild,
                    localPath = cachedPath,
                    sha256 = downloadedArtifact.sha256,
                    sizeBytes = downloadedArtifact.sizeBytes,
                    contentType = downloadedArtifact.contentType,
                )
            }
        }
    }

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
        private const val USER_AGENT_HEADER = "User-Agent"
        private const val SHA_256 = "SHA-256"
        private const val JAR_CONTENT_TYPE = "application/java-archive"
        private const val JSON_CONTENT_TYPE = "application/json"
        private const val TEXT_CONTENT_TYPE = "text/plain; charset=utf-8"
        private const val DEFAULT_PROXY_PORT = 25577
        private val SUCCESS_STATUS_RANGE = 200..299

        private fun RuntimeArtifactDefinition.cacheKey(): String = sourceUrl

        private fun RuntimeArtifactHashDocument.toManifestArtifact(): RuntimeManifestArtifact =
            RuntimeManifestArtifact(
                objectKey = objectKey,
                sourceUrl = sourceUrl,
                upstreamVersion = upstreamVersion,
                upstreamBuild = upstreamBuild,
                sha256 = sha256,
                sizeBytes = sizeBytes,
            )
    }
}
