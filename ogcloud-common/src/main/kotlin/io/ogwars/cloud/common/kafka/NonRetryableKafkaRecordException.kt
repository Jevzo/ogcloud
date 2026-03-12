package io.ogwars.cloud.common.kafka

class NonRetryableKafkaRecordException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
