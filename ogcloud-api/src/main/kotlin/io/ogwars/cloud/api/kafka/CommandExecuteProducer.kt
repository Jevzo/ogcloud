package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.api.config.KafkaConfig
import io.ogwars.cloud.api.event.CommandExecuteEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class CommandExecuteProducer(
    private val kafkaTemplate: KafkaTemplate<String, CommandExecuteEvent>
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun publishCommand(target: String, targetType: String, command: String) {
        log.info("Publishing command execute: target={}, targetType={}, command={}", target, targetType, command)

        kafkaTemplate.send(
            KafkaConfig.COMMAND_EXECUTE, target, CommandExecuteEvent(
                target = target,
                targetType = targetType,
                command = command
            )
        )
    }
}
