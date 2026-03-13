package io.ogwars.cloud.api.kafka

import io.ogwars.cloud.common.event.RuntimeRefreshRequestedEvent
import io.ogwars.cloud.common.kafka.KafkaTopics
import io.ogwars.cloud.common.model.RuntimeBundleScope
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class RuntimeRefreshProducer(
    private val kafkaTemplate: KafkaTemplate<String, RuntimeRefreshRequestedEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestRefresh(
        scope: RuntimeBundleScope,
        requestedBy: String? = null,
    ) {
        log.info("Publishing runtime refresh request: scope={}, requestedBy={}", scope, requestedBy)

        kafkaTemplate.send(
            KafkaTopics.RUNTIME_REFRESH_REQUESTED,
            scope.name,
            RuntimeRefreshRequestedEvent(
                scope = scope,
                requestedBy = requestedBy,
            ),
        )
    }
}
