package io.ogwars.cloud.controller.kafka

import io.ogwars.cloud.api.event.TemplatePushEvent
import io.ogwars.cloud.api.model.ServerState
import io.ogwars.cloud.controller.config.KafkaConfig
import io.ogwars.cloud.controller.redis.ServerRedisRepository
import io.ogwars.cloud.controller.service.KubernetesService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class TemplatePushConsumer(
    private val serverRedisRepository: ServerRedisRepository,
    private val kubernetesService: KubernetesService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [KafkaConfig.SERVER_TEMPLATE_PUSH], groupId = "ogcloud-controller")
    fun onTemplatePush(message: String) {
        val event = objectMapper.readValue(message, TemplatePushEvent::class.java)

        val server = serverRedisRepository.findById(event.serverId)
        if (server == null) {
            log.warn("Template push requested for unknown server: id={}", event.serverId)
            return
        }

        if (server.state != ServerState.RUNNING) {
            log.warn("Template push rejected, server not RUNNING: id={}, state={}", event.serverId, server.state)
            return
        }

        log.info("Triggering template push via exec: id={}, pod={}", event.serverId, server.podName)

        try {
            kubernetesService.execInContainer(
                server.podName,
                "template-pusher",
                listOf("kill", "-USR1", "1"),
            )
            log.info("Template push signal sent: id={}", event.serverId)
        } catch (e: Exception) {
            log.error("Failed to trigger template push: id={}", event.serverId, e)
        }
    }
}
