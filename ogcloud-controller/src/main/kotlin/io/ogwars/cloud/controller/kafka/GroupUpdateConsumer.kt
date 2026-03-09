package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.GroupUpdateEvent
import io.ogwars.cloud.api.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.AutoscalerService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class GroupUpdateConsumer(
    private val autoscalerService: AutoscalerService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.GROUP_UPDATE],
        groupId = "ogcloud-controller",
        containerFactory = "lightKafkaListenerFactory",
    )
    fun onGroupUpdate(message: String) {
        val event = objectMapper.readValue(message, GroupUpdateEvent::class.java)

        log.info("Group update received: groupId={}, maintenance={}", event.groupId, event.maintenance)

        autoscalerService.evaluateGroupNow(event.groupId)
    }
}
