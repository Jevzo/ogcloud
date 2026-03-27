package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.model.NpcDocument
import io.ogwars.cloud.api.model.toDefinition
import io.ogwars.cloud.common.event.NpcSyncEvent
import io.ogwars.cloud.common.event.NpcSyncOperation
import io.ogwars.cloud.common.kafka.KafkaTopics
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class NpcSyncProducer(
    private val kafkaTemplate: KafkaTemplate<String, NpcSyncEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishUpsert(npc: NpcDocument) {
        log.info("Publishing npc upsert: id={}, group={}", npc.id, npc.group)

        kafkaTemplate.send(
            KafkaTopics.NPC_SYNC,
            npc.group,
            NpcSyncEvent(
                operation = NpcSyncOperation.UPSERT,
                npc = npc.toDefinition(),
                npcId = npc.id,
                group = npc.group,
            ),
        )
    }

    fun publishDelete(
        npcId: String,
        group: String,
    ) {
        log.info("Publishing npc delete: id={}, group={}", npcId, group)

        kafkaTemplate.send(
            KafkaTopics.NPC_SYNC,
            group,
            NpcSyncEvent(
                operation = NpcSyncOperation.DELETE,
                npcId = npcId,
                group = group,
            ),
        )
    }
}
