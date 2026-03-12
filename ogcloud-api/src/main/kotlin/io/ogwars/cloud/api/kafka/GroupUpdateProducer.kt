package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.event.GroupUpdateEvent
import io.ogwars.cloud.api.model.GroupDocument
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class GroupUpdateProducer(
    private val kafkaTemplate: KafkaTemplate<String, GroupUpdateEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun publishGroupUpdate(group: GroupDocument) {
        log.info("Publishing group update: groupId={}, maintenance={}", group.id, group.maintenance)

        kafkaTemplate.send(
            KafkaTopics.GROUP_UPDATE,
            group.id,
            GroupUpdateEvent(
                groupId = group.id,
                type = group.type,
                maintenance = group.maintenance,
            ),
        )
    }
}
