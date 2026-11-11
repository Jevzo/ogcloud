package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.api.auth.users.UserManagement
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.store.ConflictException
import org.springframework.stereotype.Service

/** The caller's own passkeys (D-0213): removing one needs the password (D-0217), and an owner keeps a factor (D-0097). */
@Service
class PasskeyManagement(
    private val passkeys: Passkeys,
    private val totp: TotpService,
    private val users: UserManagement,
) {
    fun list(actor: Actor): List<PasskeyView> =
        passkeys.list(users.self(actor).id).map(PasskeyView::of).sortedBy(PasskeyView::createdAt)

    fun rename(
        actor: Actor,
        id: String,
        label: String,
    ): PasskeyView {
        val name = label.trim()
        require(name.length in 1..MAX_LABEL) { "A passkey's name has 1 to $MAX_LABEL characters" }
        val record = passkeys.find(users.self(actor).id, id)
        passkeys.rename(record, name)
        return PasskeyView.of(record).copy(label = name)
    }

    fun remove(
        actor: Actor,
        id: String,
        password: String,
    ) {
        val user = users.withOwnPassword(actor, password)
        val record = passkeys.find(user.id, id)
        if (user.isOwner && !totp.isEnrolled(user.id) && passkeys.list(user.id).size == 1) {
            throw ConflictException(
                "Owners need a second factor; add an authenticator app before removing this passkey",
            )
        }
        passkeys.remove(record)
    }

    private companion object {
        const val MAX_LABEL = 64
    }
}
