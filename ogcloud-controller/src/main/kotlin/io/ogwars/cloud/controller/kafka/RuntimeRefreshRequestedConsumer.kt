package io.ogwars.cloud.controller.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.ogwars.cloud.common.event.RuntimeRefreshRequestedEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.controller.service.RuntimeBundleService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class RuntimeRefreshRequestedConsumer(
    private val runtimeBundleService: RuntimeBundleService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaTopics.RUNTIME_REFRESH_REQUESTED],
        groupId = "ogcloud-controller",
        containerFactory = "singleKafkaListenerFactory",
    )
    fun onRuntimeRefreshRequested(message: String) {
        val event = objectMapper.readValue(message, RuntimeRefreshRequestedEvent::class.java)

        log.info("Runtime refresh request received: scope={}, requestedBy={}", event.scope, event.requestedBy)

        runtimeBundleService.refreshScope(event.scope, event.requestedBy)
    }
}
