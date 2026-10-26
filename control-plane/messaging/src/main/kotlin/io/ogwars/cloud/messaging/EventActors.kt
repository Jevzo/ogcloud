package io.ogwars.cloud.messaging

import io.ogwars.cloud.contracts.events.v1.Actor.SubjectCase
import io.ogwars.cloud.contracts.events.v1.EventMetadata
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType

/** Who caused the event; an event without an actor is malformed and never retried. */
fun EventMetadata.actor(): Actor {
    val subject = actor
    return when (subject.subjectCase) {
        SubjectCase.USER_ID -> Actor(ActorType.USER, subject.userId)
        SubjectCase.CLIENT_ID -> Actor(ActorType.CLIENT, subject.clientId)
        SubjectCase.PLAYER_UUID -> Actor(ActorType.PLAYER, subject.playerUuid)
        SubjectCase.SYSTEM -> Actor(ActorType.SYSTEM, subject.system)
        SubjectCase.SUBJECT_NOT_SET, null -> throw NonRetryableEventException("Event $eventId has no actor")
    }
}
