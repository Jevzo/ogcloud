package io.ogwars.cloud.messaging

import io.ogwars.cloud.contracts.events.v1.EventMetadata
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.common.IdGenerator
import com.google.protobuf.Timestamp
import java.time.Clock
import io.ogwars.cloud.contracts.events.v1.Actor as ActorMessage

/** Stamps every event with a UUIDv7 id, the time, the producing service and the actor (field 1 of every event). */
class EventMetadataFactory(
    private val clock: Clock,
    private val ids: IdGenerator,
    private val producer: String,
) {
    fun create(actor: Actor): EventMetadata {
        val now = clock.instant()
        return EventMetadata
            .newBuilder()
            .setEventId(ids.next().toString())
            .setOccurredAt(Timestamp.newBuilder().setSeconds(now.epochSecond).setNanos(now.nano))
            .setProducer(producer)
            .setActor(toMessage(actor))
            .build()
    }

    private fun toMessage(actor: Actor): ActorMessage {
        val builder = ActorMessage.newBuilder()
        when (actor.type) {
            ActorType.USER -> builder.setUserId(actor.id)
            ActorType.CLIENT -> builder.setClientId(actor.id)
            ActorType.PLAYER -> builder.setPlayerUuid(actor.id)
            ActorType.SYSTEM -> builder.setSystem(actor.id)
        }
        return builder.build()
    }
}
