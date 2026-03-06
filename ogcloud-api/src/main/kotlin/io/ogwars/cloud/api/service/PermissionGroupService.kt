package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.*
import io.ogwars.cloud.api.exception.PermissionGroupAlreadyExistsException
import io.ogwars.cloud.api.exception.PermissionGroupNotFoundException
import io.ogwars.cloud.api.kafka.PermissionUpdateProducer
import io.ogwars.cloud.api.model.DisplayConfig
import io.ogwars.cloud.api.model.PermissionConfig
import io.ogwars.cloud.api.model.PermissionGroupDocument
import io.ogwars.cloud.api.redis.PlayerRedisRepository
import io.ogwars.cloud.api.repository.PermissionGroupRepository
import io.ogwars.cloud.api.repository.PlayerRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.find
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service

@Service
class PermissionGroupService(
    private val permissionGroupRepository: PermissionGroupRepository,
    private val mongoTemplate: MongoTemplate,
    private val playerRepository: PlayerRepository,
    private val playerRedisRepository: PlayerRedisRepository,
    private val permissionUpdateProducer: PermissionUpdateProducer
) {

    fun listAll(query: String?, page: Int, size: Int?): PaginatedResponse<PermissionGroupResponse> {
        val pageRequest = PaginationSupport.toPageRequest(page, size)
        val queryObject = Query()

        PaginationSupport.buildSearchCriteria(
            query,
            "id",
            "name",
            "display.chatPrefix",
            "display.chatSuffix",
            "display.nameColor",
            "display.tabPrefix",
            "permissions"
        )?.let(queryObject::addCriteria)

        val totalItems = mongoTemplate.count<PermissionGroupDocument>(queryObject)

        queryObject.with(
            Sort.by(
                Sort.Order.desc("weight"),
                Sort.Order.asc("name")
            )
        ).with(pageRequest)

        val groups = mongoTemplate.find<PermissionGroupDocument>(queryObject)
            .map { it.toResponse() }

        return PaginationSupport.toResponse(groups, page, pageRequest.pageSize, totalItems)
    }

    fun getByName(name: String): PermissionGroupResponse {
        val group = permissionGroupRepository.findById(name)
            .orElseThrow { PermissionGroupNotFoundException(name) }
        return group.toResponse()
    }

    fun create(request: CreatePermissionGroupRequest): PermissionGroupResponse {
        val document = PermissionGroupDocument(
            id = request.id,
            name = request.name,
            display = DisplayConfig(
                chatPrefix = request.display.chatPrefix,
                chatSuffix = request.display.chatSuffix,
                nameColor = request.display.nameColor,
                tabPrefix = request.display.tabPrefix
            ),
            weight = request.weight,
            default = request.default,
            permissions = request.permissions
        )

        val saved = try {
            permissionGroupRepository.save(document)
        } catch (_: DuplicateKeyException) {
            throw PermissionGroupAlreadyExistsException(request.id)
        }

        return saved.toResponse()
    }

    fun update(name: String, request: UpdatePermissionGroupRequest): PermissionGroupResponse {
        val existing = permissionGroupRepository.findById(name)
            .orElseThrow { PermissionGroupNotFoundException(name) }

        val updatedDisplay = request.display?.let { req ->
            existing.display.copy(
                chatPrefix = req.chatPrefix,
                chatSuffix = req.chatSuffix,
                nameColor = req.nameColor,
                tabPrefix = req.tabPrefix
            )
        } ?: existing.display

        val saved = permissionGroupRepository.save(
            existing.copy(
                name = request.name ?: existing.name,
                display = updatedDisplay,
                weight = request.weight ?: existing.weight,
                default = request.default ?: existing.default,
                permissions = request.permissions ?: existing.permissions
            )
        )

        publishUpdateForGroupPlayers(name)

        return saved.toResponse()
    }

    fun delete(name: String) {
        val existing = permissionGroupRepository.findById(name)
            .orElseThrow { PermissionGroupNotFoundException(name) }

        if (existing.default) {
            throw IllegalArgumentException("Cannot delete the default permission group")
        }

        val defaultGroup = permissionGroupRepository.findByDefaultTrue()
            ?: throw IllegalStateException("No default permission group configured")

        val players = playerRepository.findByPermission_Group(name)
        for (player in players) {
            playerRepository.save(
                player.copy(
                    permission = PermissionConfig(
                        group = defaultGroup.id,
                        length = PERMANENT_PERMISSION_LENGTH,
                        endMillis = PERMANENT_PERMISSION_END_MILLIS
                    )
                )
            )

            permissionUpdateProducer.publishPermissionUpdate(
                player.id,
                defaultGroup,
                PERMANENT_PERMISSION_END_MILLIS,
                SYSTEM_UPDATED_BY
            )
        }

        permissionGroupRepository.deleteById(name)
    }

    fun addPermission(name: String, permission: String): PermissionGroupResponse {
        val existing = permissionGroupRepository.findById(name)
            .orElseThrow { PermissionGroupNotFoundException(name) }

        if (existing.permissions.contains(permission)) {
            return existing.toResponse()
        }

        val saved = permissionGroupRepository.save(existing.copy(permissions = existing.permissions + permission))

        publishUpdateForGroupPlayers(name)

        return saved.toResponse()
    }

    fun removePermission(name: String, permission: String): PermissionGroupResponse {
        val existing = permissionGroupRepository.findById(name)
            .orElseThrow { PermissionGroupNotFoundException(name) }

        val saved = permissionGroupRepository.save(existing.copy(permissions = existing.permissions - permission))

        publishUpdateForGroupPlayers(name)

        return saved.toResponse()
    }

    private fun publishUpdateForGroupPlayers(groupId: String) {
        val group = permissionGroupRepository.findById(groupId).orElse(null) ?: return
        val onlineUuids = playerRedisRepository.findOnlinePlayerUuids()

        playerRepository.findByPermission_Group(groupId)
            .filter { it.id in onlineUuids }
            .forEach { player ->
                permissionUpdateProducer.publishPermissionUpdate(
                    player.id,
                    group,
                    player.permission.endMillis,
                    API_UPDATED_BY
                )
            }
    }

    companion object {
        private const val API_UPDATED_BY = "api"
        private const val SYSTEM_UPDATED_BY = "system"
        private const val PERMANENT_PERMISSION_LENGTH = -1L
        private const val PERMANENT_PERMISSION_END_MILLIS = -1L
    }
}
