package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.exception.GroupAlreadyExistsException
import io.ogwars.cloud.api.exception.GroupDeletionTimeoutException
import io.ogwars.cloud.api.exception.GroupNotFoundException
import io.ogwars.cloud.api.exception.GroupRestartTimeoutException
import io.ogwars.cloud.api.kafka.GroupUpdateProducer
import io.ogwars.cloud.api.kafka.ServerRequestProducer
import io.ogwars.cloud.api.kafka.ServerStopProducer
import io.ogwars.cloud.api.model.GroupDocument
import io.ogwars.cloud.api.model.ScalingConfig
import io.ogwars.cloud.api.model.ServerDocument
import io.ogwars.cloud.api.redis.ServerRedisRepository
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.common.model.BackendRuntimeProfile
import io.ogwars.cloud.common.model.GroupType
import io.ogwars.cloud.common.model.ServerState
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class GroupService(
    private val groupRepository: GroupRepository,
    private val mongoTemplate: MongoTemplate,
    private val groupUpdateProducer: GroupUpdateProducer,
    private val serverRedisRepository: ServerRedisRepository,
    private val serverRequestProducer: ServerRequestProducer,
    private val serverStopProducer: ServerStopProducer,
    private val auditLogService: AuditLogService,
    private val groupOperationTaskExecutor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun listAll(
        query: String?,
        page: Int,
        size: Int?,
    ): PaginatedResponse<GroupResponse> {
        val pageRequest = PaginationSupport.toPageRequest(page, size)
        val queryObject = Query()

        PaginationSupport
            .buildSearchCriteria(
                query,
                "id",
                "type",
                "templateBucket",
                "templatePath",
                "templateVersion",
                "serverImage",
                "runtimeProfile",
            )?.let(queryObject::addCriteria)

        val totalItems = mongoTemplate.count<GroupDocument>(queryObject)

        queryObject.with(Sort.by(Sort.Order.asc("id"))).with(pageRequest)

        val groups =
            mongoTemplate
                .find<GroupDocument>(queryObject)
                .map { it.toResponse() }

        return PaginationSupport.toResponse(groups, page, pageRequest.pageSize, totalItems)
    }

    fun getByName(name: String): GroupResponse = requireGroup(name).toResponse()

    fun create(request: CreateGroupRequest): GroupResponse {
        val document = request.toDocument()
        validateScaling(document.scaling)
        validateRuntimeSettings(
            type = document.type,
            runtimeProfile = document.runtimeProfile,
            serverImage = document.serverImage,
            requireExplicitRuntimeProfile = true,
        )

        val saved =
            try {
                groupRepository.save(document)
            } catch (_: DuplicateKeyException) {
                throw GroupAlreadyExistsException(request.id)
            }

        auditLogService.logApiAction(
            action = "GROUP_CREATED",
            targetType = "GROUP",
            targetId = saved.id,
            summary = "Created group ${saved.id}",
            metadata =
                buildMap {
                    put("type", saved.type.name)
                    saved.resolvedRuntimeProfile()?.let { put("runtimeProfile", it.name) }
                },
        )

        return saved.toResponse()
    }

    fun update(
        name: String,
        request: UpdateGroupRequest,
    ): GroupResponse {
        val existing = requireGroup(name)
        val updatedScaling = request.scaling?.toModel() ?: existing.scaling
        val updatedRuntimeProfile = request.runtimeProfile ?: existing.runtimeProfile
        val updatedServerImage = request.serverImage ?: existing.serverImage
        validateScaling(updatedScaling)
        validateRuntimeSettings(
            type = existing.type,
            runtimeProfile = updatedRuntimeProfile,
            serverImage = updatedServerImage,
            requireExplicitRuntimeProfile = false,
        )

        val saved =
            groupRepository.save(
                existing.copy(
                    templateBucket = request.templateBucket ?: existing.templateBucket,
                    templatePath = request.templatePath ?: existing.templatePath,
                    templateVersion = request.templateVersion ?: existing.templateVersion,
                    scaling = updatedScaling,
                    resources = request.resources?.toModel() ?: existing.resources,
                    jvmFlags = request.jvmFlags ?: existing.jvmFlags,
                    drainTimeoutSeconds = request.drainTimeoutSeconds ?: existing.drainTimeoutSeconds,
                    serverImage = updatedServerImage,
                    runtimeProfile = updatedRuntimeProfile,
                    storageSize = request.storageSize ?: existing.storageSize,
                    updatedAt = Instant.now(),
                ),
            )

        groupUpdateProducer.publishGroupUpdate(saved)

        auditLogService.logApiAction(
            action = "GROUP_UPDATED",
            targetType = "GROUP",
            targetId = saved.id,
            summary = "Updated group ${saved.id}",
        )

        return saved.toResponse()
    }

    fun setMaintenance(
        name: String,
        enabled: Boolean,
    ): GroupResponse {
        val existing = requireGroup(name)
        val saved = groupRepository.save(existing.copy(maintenance = enabled, updatedAt = Instant.now()))

        groupUpdateProducer.publishGroupUpdate(saved)

        auditLogService.logApiAction(
            action = "GROUP_MAINTENANCE_UPDATED",
            targetType = "GROUP",
            targetId = saved.id,
            summary = "Set maintenance=$enabled for group ${saved.id}",
            metadata = mapOf("maintenance" to enabled.toString()),
        )

        return saved.toResponse()
    }

    fun delete(name: String) {
        val existing = requireGroup(name)

        if (serverRedisRepository.findByGroup(name).isNotEmpty()) {
            val deletingGroup = ensureMaintenance(existing)

            stopServersInGroup(deletingGroup) { activeServerIds ->
                GroupDeletionTimeoutException(deletingGroup.id, activeServerIds)
            }
        }

        groupRepository.deleteById(name)

        auditLogService.logApiAction(
            action = "GROUP_DELETED",
            targetType = "GROUP",
            targetId = name,
            summary = "Deleted group $name",
        )
    }

    fun requestRestart(name: String) {
        val existing = requireGroup(name)

        if (!existing.maintenance) {
            throw IllegalArgumentException("Group must be in maintenance before restart: $name")
        }

        groupOperationTaskExecutor.execute {
            try {
                restart(existing)
            } catch (ex: Exception) {
                log.error("Async group restart failed: id={}", name, ex)
            }
        }

        auditLogService.logApiAction(
            action = "GROUP_RESTART_REQUESTED",
            targetType = "GROUP",
            targetId = name,
            summary = "Requested asynchronous restart for group $name",
        )
    }

    private fun ensureMaintenance(group: GroupDocument): GroupDocument {
        if (group.maintenance) {
            return group
        }

        val saved = groupRepository.save(group.copy(maintenance = true, updatedAt = Instant.now()))

        groupUpdateProducer.publishGroupUpdate(saved)

        return saved
    }

    private fun restart(group: GroupDocument) {
        stopServersInGroup(group) { activeServerIds ->
            GroupRestartTimeoutException(group.id, activeServerIds)
        }

        val currentGroup = groupRepository.findById(group.id).orElse(null)

        if (currentGroup == null) {
            log.warn("Group disappeared before restart could complete: id={}", group.id)
            return
        }

        requestRestartServers(currentGroup)
    }

    private fun requestRestartServers(group: GroupDocument) {
        repeat(group.scaling.minOnline) {
            val serverId = serverRequestProducer.requestServer(group.id)
            log.info(
                "Requested replacement server for group restart: group={}, serverId={}",
                group.id,
                serverId,
            )
        }
    }

    private fun stopServersInGroup(
        group: GroupDocument,
        timeoutException: (List<String>) -> RuntimeException,
    ) {
        val requestedStopIds = mutableSetOf<String>()
        val deadline = Instant.now().plusSeconds(calculateDeleteTimeoutSeconds(group))

        while (true) {
            val servers = serverRedisRepository.findByGroup(group.id)

            if (servers.isEmpty()) {
                break
            }

            requestStopsForActiveServers(group.id, servers, requestedStopIds)
            requireBeforeDeadline(deadline, servers, timeoutException)

            sleepForDeletionPoll(group.id)
        }
    }

    private fun requestStopsForActiveServers(
        groupId: String,
        servers: List<ServerDocument>,
        requestedStopIds: MutableSet<String>,
    ) {
        servers
            .filterNot(::isStoppedOrStopping)
            .forEach { server ->
                if (requestedStopIds.add(server.id)) {
                    serverStopProducer.stopServer(server.id)
                    log.info(
                        "Requested server stop before group deletion: group={}, serverId={}, state={}",
                        groupId,
                        server.id,
                        server.state,
                    )
                }
            }
    }

    private fun requireBeforeDeadline(
        deadline: Instant,
        servers: List<ServerDocument>,
        timeoutException: (List<String>) -> RuntimeException,
    ) {
        if (Instant.now().isAfter(deadline)) {
            throw timeoutException(servers.map(ServerDocument::id))
        }
    }

    private fun isStoppedOrStopping(server: ServerDocument): Boolean =
        server.state == ServerState.STOPPING || server.state == ServerState.STOPPED

    private fun validateScaling(scaling: ScalingConfig) {
        if (scaling.minOnline > scaling.maxInstances) {
            throw IllegalArgumentException(
                "minOnline (${scaling.minOnline}) cannot exceed maxInstances (${scaling.maxInstances})",
            )
        }
    }

    private fun validateRuntimeSettings(
        type: GroupType,
        runtimeProfile: BackendRuntimeProfile?,
        serverImage: String,
        requireExplicitRuntimeProfile: Boolean,
    ) {
        if (type == GroupType.PROXY) {
            if (runtimeProfile != null) {
                throw IllegalArgumentException("runtimeProfile must be omitted for proxy groups")
            }
            if (!isVelocityImage(serverImage)) {
                throw IllegalArgumentException("Proxy groups must use a velocity image")
            }
            return
        }

        if (!isPaperImage(serverImage)) {
            throw IllegalArgumentException("Backend groups must use a paper image")
        }

        val effectiveRuntimeProfile =
            runtimeProfile
                ?: if (requireExplicitRuntimeProfile) {
                    throw IllegalArgumentException("runtimeProfile is required for backend groups")
                } else {
                    BackendRuntimeProfile.MODERN_1_21_11
                }

        when (effectiveRuntimeProfile) {
            BackendRuntimeProfile.LEGACY_1_8_8 -> {
                if (serverImage != LEGACY_SERVER_IMAGE) {
                    throw IllegalArgumentException(
                        "LEGACY_1_8_8 groups must use serverImage=$LEGACY_SERVER_IMAGE",
                    )
                }
            }

            BackendRuntimeProfile.MODERN_1_21_11 -> {
                if (imageTag(serverImage) != effectiveRuntimeProfile.minecraftVersion) {
                    throw IllegalArgumentException(
                        "MODERN_1_21_11 groups must use a paper image tagged ${effectiveRuntimeProfile.minecraftVersion}",
                    )
                }
            }
        }
    }

    private fun isVelocityImage(serverImage: String): Boolean {
        val name = imageName(serverImage)
        return name == "velocity" || name.endsWith("/velocity")
    }

    private fun isPaperImage(serverImage: String): Boolean {
        val name = imageName(serverImage)
        return name == "paper" || name.endsWith("/paper")
    }

    private fun imageName(serverImage: String): String {
        val normalized = serverImage.substringBefore('@')
        val lastColon = normalized.lastIndexOf(':')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastColon > lastSlash) {
            normalized.substring(0, lastColon).lowercase()
        } else {
            normalized.lowercase()
        }
    }

    private fun imageTag(serverImage: String): String {
        val normalized = serverImage.substringBefore('@')
        val lastColon = normalized.lastIndexOf(':')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastColon > lastSlash) {
            normalized.substring(lastColon + 1)
        } else {
            ""
        }
    }

    private fun requireGroup(name: String): GroupDocument =
        groupRepository
            .findById(name)
            .orElseThrow { GroupNotFoundException(name) }

    private fun calculateDeleteTimeoutSeconds(group: GroupDocument): Long {
        val requestedDrainSeconds = group.drainTimeoutSeconds.toLong() + DELETE_TIMEOUT_BUFFER_SECONDS
        return requestedDrainSeconds.coerceAtLeast(MIN_DELETE_WAIT_SECONDS)
    }

    private fun sleepForDeletionPoll(groupId: String) {
        try {
            Thread.sleep(DELETE_POLL_INTERVAL_MS)
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Interrupted while waiting for group deletion: $groupId", ex)
        }
    }

    companion object {
        private const val DELETE_POLL_INTERVAL_MS = 1_000L
        private const val DELETE_TIMEOUT_BUFFER_SECONDS = 45L
        private const val MIN_DELETE_WAIT_SECONDS = 30L
        private const val LEGACY_SERVER_IMAGE = "ogwarsdev/paper:1.8.8"
    }
}
