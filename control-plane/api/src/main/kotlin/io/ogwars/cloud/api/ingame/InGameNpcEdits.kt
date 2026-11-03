package io.ogwars.cloud.api.ingame

import io.ogwars.cloud.api.audit.AuditTrail
import io.ogwars.cloud.api.npcs.NPCS_PATH
import io.ogwars.cloud.api.npcs.NpcManagement
import io.ogwars.cloud.contracts.internal.v1.NpcChangeRequested
import io.ogwars.cloud.contracts.internal.v1.NpcChangeRequested.ChangeCase
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.npc.Npc
import io.ogwars.cloud.livestate.toDomain
import io.ogwars.cloud.messaging.KafkaComponents
import io.ogwars.cloud.messaging.KafkaTopics
import io.ogwars.cloud.messaging.ListenerContainers
import io.ogwars.cloud.messaging.NonRetryableEventException
import io.ogwars.cloud.messaging.actor
import io.ogwars.cloud.messaging.protobufContainer
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * `ogcloud.npc.change`: NPC edits of in-game admins, applied like dashboard edits with the player as the audited
 * actor (D-0129). The gateway checked the admin; an edit only touches NPCs of the group it came from.
 */
@Component
class InGameNpcEdits(
    components: KafkaComponents,
    template: KafkaTemplate<String, ByteArray>,
    private val npcs: NpcManagement,
    private val trail: AuditTrail,
) : ListenerContainers() {
    override val containers =
        listOf(
            components
                .listenerContainerFactory(GROUP, template)
                .protobufContainer(KafkaTopics.NPC_CHANGE, NpcChangeRequested.parser(), ::handle),
        )

    fun handle(change: NpcChangeRequested) {
        val actor = change.metadata.actor()
        applyOrRefuse(change.metadata.eventId) {
            val group = GroupId(change.groupId)
            when (change.changeCase) {
                ChangeCase.CREATE -> {
                    val npc = change.create.toDomain().inGroup(group)
                    npcs.create(actor, npc)
                    audit(actor, "POST $NPCS_PATH", npc.id)
                }

                ChangeCase.UPDATE -> {
                    val npc = change.update.toDomain().inGroup(group)
                    npcs.get(npc.id).inGroup(group)
                    npcs.update(actor, npc)
                    audit(actor, "PUT $NPCS_PATH/{id}", npc.id)
                }

                ChangeCase.DELETE_NPC_ID -> {
                    // Already gone: a redelivered delete changes nothing.
                    val npc = npcs.find(UUID.fromString(change.deleteNpcId))?.inGroup(group) ?: return
                    npcs.delete(actor, npc.id)
                    audit(actor, "DELETE $NPCS_PATH/{id}", npc.id)
                }

                ChangeCase.CHANGE_NOT_SET, null -> {
                    throw NonRetryableEventException("NPC change ${change.metadata.eventId} carries no change")
                }
            }
        }
    }

    private fun Npc.inGroup(group: GroupId): Npc =
        also { require(groupId == group) { "NPC $id belongs to ${groupId.value}, not ${group.value}" } }

    private fun audit(
        actor: Actor,
        action: String,
        id: UUID,
    ) = trail.record(actor, action, TARGET_TYPE, id.toString())

    private companion object {
        const val GROUP = "ogcloud-api-npc-changes"
        const val TARGET_TYPE = "npcs"
    }
}
