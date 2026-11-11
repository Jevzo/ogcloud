package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.api.auth.users.UserManagement
import io.ogwars.cloud.domain.audit.Actor
import org.springframework.stereotype.Service

/** The caller's own passkeys (D-0213): removing one needs the password (D-0217), and an owner keeps a factor (D-0097). */
@Service
class PasskeyManagement(
    private val passkeys: Passkeys,
    private val guard: FactorGuard,
    private val users: UserManagement,
) {
    fun list(actor: Actor): List<PasskeyView> =
        passkeys.list(users.self(actor).id).map(PasskeyView::of).sortedBy(PasskeyView::createdAt)

    fun rename(
        actor: Actor,
        id: String,
        label: String,
    ): PasskeyView {
        val name = passkeyLabel(label)
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
        // Another user's or an unknown passkey is a 404 before any rule about the caller's own factors.
        val record = passkeys.find(user.id, id)
        guard.remove(user) { passkeys.remove(record) }
    }
}
