package io.ogwars.cloud.contracts

import com.google.protobuf.Timestamp
import java.time.Duration
import java.time.Instant
import com.google.protobuf.Duration as ProtoDuration

fun Instant.toTimestamp(): Timestamp =
    Timestamp
        .newBuilder()
        .setSeconds(epochSecond)
        .setNanos(nano)
        .build()

fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())

fun Duration.toProtoDuration(): ProtoDuration =
    ProtoDuration
        .newBuilder()
        .setSeconds(seconds)
        .setNanos(nano)
        .build()

fun ProtoDuration.toJavaDuration(): Duration = Duration.ofSeconds(seconds, nanos.toLong())
