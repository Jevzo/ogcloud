package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.event.PermissionUpdateEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.api.model.PermissionGroupDocument
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PermissionUpdateProducer(
    private val kafkaTemplate: KafkaTemplate<String, PermissionUpdateEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishPermissionUpdate(
        uuid: String,
        group: PermissionGroupDocument,
        permissionEndMillis: Long,
        updatedBy: String,
    ) {
        log.info("Publishing permission update: uuid={}, groupId={}", uuid, group.id)

        kafkaTemplate.send(
            KafkaTopics.PERMISSION_UPDATE,
            uuid,
            PermissionUpdateEvent(
                uuid = uuid,
                groupId = group.id,
                groupName = group.name,
                permissions = group.permissions,
                display = group.display,
                weight = group.weight,
                permissionEndMillis = permissionEndMillis,
                updatedBy = updatedBy,
            ),
        )
    }
}
