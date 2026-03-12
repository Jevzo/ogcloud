package io.ogwars.cloud.common.kafka

object KafkaHeaderNames {
    const val RETRY_ATTEMPT = "ogcloud-retry-attempt"
    const val DLT_ORIGINAL_TOPIC = "kafka_dlt-original-topic"
    const val DLT_ORIGINAL_PARTITION = "kafka_dlt-original-partition"
    const val DLT_ORIGINAL_OFFSET = "kafka_dlt-original-offset"
    const val DLT_ORIGINAL_CONSUMER_GROUP = "kafka_dlt-original-consumer-group"
    const val DLT_EXCEPTION_FQCN = "kafka_dlt-exception-fqcn"
    const val DLT_EXCEPTION_MESSAGE = "kafka_dlt-exception-message"
}
