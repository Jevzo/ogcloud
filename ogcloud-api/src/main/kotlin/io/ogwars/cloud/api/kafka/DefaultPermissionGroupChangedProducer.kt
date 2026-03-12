package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.event.DefaultPermissionGroupChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class DefaultPermissionGroupChangedProducer(
    private val kafkaTemplate: KafkaTemplate<String, DefaultPermissionGroupChangedEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishDefaultPermissionGroupChanged(groupId: String) {
        log.info("Publishing default permission group changed: groupId={}", groupId)

        kafkaTemplate.send(
            KafkaTopics.DEFAULT_PERMISSION_GROUP_CHANGED,
            groupId,
            DefaultPermissionGroupChangedEvent(groupId = groupId),
        )
    }
}
