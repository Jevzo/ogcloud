package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.event.PermissionGroupUpdatedEvent
import io.ogwars.cloud.api.model.PermissionGroupDocument
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PermissionGroupUpdatedProducer(
    private val kafkaTemplate: KafkaTemplate<String, PermissionGroupUpdatedEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishPermissionGroupUpserted(group: PermissionGroupDocument) {
        log.info("Publishing permission group upsert: groupId={}", group.id)

        kafkaTemplate.send(
            KafkaTopics.PERMISSION_GROUP_UPDATED,
            group.id,
            PermissionGroupUpdatedEvent(
                groupId = group.id,
                group = group,
                deleted = false,
            ),
        )
    }

    fun publishPermissionGroupDeleted(groupId: String) {
        log.info("Publishing permission group delete: groupId={}", groupId)

        kafkaTemplate.send(
            KafkaTopics.PERMISSION_GROUP_UPDATED,
            groupId,
            PermissionGroupUpdatedEvent(
                groupId = groupId,
                group = null,
                deleted = true,
            ),
        )
    }
}
