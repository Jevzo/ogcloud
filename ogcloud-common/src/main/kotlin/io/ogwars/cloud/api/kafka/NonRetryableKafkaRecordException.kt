package io.ogwars.cloud.api.kafka

class NonRetryableKafkaRecordException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
