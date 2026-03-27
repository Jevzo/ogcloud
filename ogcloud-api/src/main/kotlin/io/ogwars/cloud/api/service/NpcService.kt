package io.ogwars.cloud.api.service

import io.ogwars.cloud.api.dto.CreateNpcRequest
import io.ogwars.cloud.api.dto.NpcActionRequest
import io.ogwars.cloud.api.dto.NpcResponse
import io.ogwars.cloud.api.dto.UpdateNpcRequest
import io.ogwars.cloud.api.dto.merge
import io.ogwars.cloud.api.dto.toModel
import io.ogwars.cloud.api.dto.toResponse
import io.ogwars.cloud.api.exception.NpcAlreadyExistsException
import io.ogwars.cloud.api.exception.NpcNotFoundException
import io.ogwars.cloud.api.kafka.NpcSyncProducer
import io.ogwars.cloud.api.model.NpcDocument
import io.ogwars.cloud.api.repository.GroupRepository
import io.ogwars.cloud.api.repository.NpcRepository
import io.ogwars.cloud.common.model.NpcClickAction
import io.ogwars.cloud.common.model.NpcClickActionType
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NpcService(
    private val npcRepository: NpcRepository,
    private val groupRepository: GroupRepository,
    private val npcSyncProducer: NpcSyncProducer,
    private val auditLogService: AuditLogService,
) {
    fun list(group: String?): List<NpcResponse> {
        val npcs =
            if (group.isNullOrBlank()) {
                npcRepository.findAll()
            } else {
                npcRepository.findAllByGroup(group)
            }

        return npcs
            .sortedWith(compareBy<NpcDocument> { it.group }.thenBy { it.id })
            .map(NpcDocument::toResponse)
    }

    fun get(id: String): NpcResponse = requireNpc(id).toResponse()

    fun create(request: CreateNpcRequest): NpcResponse {
        requireGroupExists(request.group)

        val document =
            NpcDocument(
                id = request.id,
                group = request.group,
                location = request.location.toModel(),
                title = request.title,
                subtitle = request.subtitle,
                model = request.model,
                skin = request.skin?.toModel(),
                lookAt = request.lookAt.toModel(),
                leftAction = validateAction(request.leftAction),
                rightAction = validateAction(request.rightAction),
            )

        val saved =
            try {
                npcRepository.save(document)
            } catch (_: DuplicateKeyException) {
                throw NpcAlreadyExistsException(request.id)
            }

        npcSyncProducer.publishUpsert(saved)

        auditLogService.logApiAction(
            action = "NPC_CREATED",
            targetType = "NPC",
            targetId = saved.id,
            summary = "Created npc ${saved.id}",
            metadata = mapOf("group" to saved.group),
        )

        return saved.toResponse()
    }

    fun update(
        id: String,
        request: UpdateNpcRequest,
    ): NpcResponse {
        val existing = requireNpc(id)

        val saved =
            npcRepository.save(
                existing.copy(
                    location = request.location?.toModel() ?: existing.location,
                    title = request.title ?: existing.title,
                    subtitle = request.subtitle ?: existing.subtitle,
                    model = request.model ?: existing.model,
                    skin =
                        when {
                            request.clearSkin -> null
                            request.skin != null -> request.skin.toModel()
                            else -> existing.skin
                        },
                    lookAt = request.lookAt?.merge(existing.lookAt) ?: existing.lookAt,
                    leftAction = request.leftAction?.let(::validateAction) ?: existing.leftAction,
                    rightAction = request.rightAction?.let(::validateAction) ?: existing.rightAction,
                    updatedAt = Instant.now(),
                ),
            )

        npcSyncProducer.publishUpsert(saved)

        auditLogService.logApiAction(
            action = "NPC_UPDATED",
            targetType = "NPC",
            targetId = saved.id,
            summary = "Updated npc ${saved.id}",
            metadata = mapOf("group" to saved.group),
        )

        return saved.toResponse()
    }

    fun delete(id: String) {
        val existing = requireNpc(id)

        npcRepository.deleteById(id)
        npcSyncProducer.publishDelete(existing.id, existing.group)

        auditLogService.logApiAction(
            action = "NPC_DELETED",
            targetType = "NPC",
            targetId = existing.id,
            summary = "Deleted npc ${existing.id}",
            metadata = mapOf("group" to existing.group),
        )
    }

    private fun validateAction(request: NpcActionRequest): NpcClickAction =
        when (request.type) {
            NpcClickActionType.NONE -> NpcClickAction()

            NpcClickActionType.TRANSFER -> {
                val transfer =
                    request.transfer ?: throw IllegalArgumentException(
                        "transfer action requires a transfer configuration",
                    )

                requireGroupExists(transfer.group)

                NpcClickAction(
                    type = NpcClickActionType.TRANSFER,
                    targetGroup = transfer.group,
                    routingStrategy = transfer.routingStrategy,
                )
            }
        }

    private fun requireGroupExists(group: String) {
        if (!groupRepository.existsById(group)) {
            throw IllegalArgumentException("Group not found: $group")
        }
    }

    private fun requireNpc(id: String): NpcDocument =
        npcRepository
            .findById(id)
            .orElseThrow { NpcNotFoundException(id) }
}
