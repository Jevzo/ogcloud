package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component
import java.util.UUID

/** Only owners change permissions, and only owners touch owners (D-0094). */
@Component
class Owners(
    private val users: WebUserStore,
) {
    fun requireOwner(actor: Actor) {
        if (!isOwner(actor)) throw AccessDeniedException("Only owners may change permissions")
    }

    fun requireMayManage(
        actor: Actor,
        target: WebUser,
    ) {
        if (target.isOwner && !isOwner(actor)) throw AccessDeniedException("Only owners may manage owners")
    }

    // Read from the database rather than the token, so a demotion counts at once.
    private fun isOwner(actor: Actor): Boolean =
        actor.type == ActorType.USER &&
            users.find(UUID.fromString(actor.id))?.value?.let { it.isOwner && it.enabled } == true
}
