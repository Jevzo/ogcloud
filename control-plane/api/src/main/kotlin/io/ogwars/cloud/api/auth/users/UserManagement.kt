package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.auth.Logins
import io.ogwars.cloud.api.auth.Owners
import io.ogwars.cloud.api.auth.hash
import io.ogwars.cloud.api.auth.mfa.Passkeys
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.PasswordPolicy
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.UUID

/**
 * Dashboard users (D-0094): roles are assigned by owners, and every change that matters ends the user's logins in
 * the same transaction, so a change never commits while the old logins live on.
 */
@Service
class UserManagement(
    private val users: WebUserStore,
    private val roles: RoleStore,
    private val owners: Owners,
    private val passwords: PasswordEncoder,
    private val logins: Logins,
    private val passkeys: Passkeys,
    private val ids: IdGenerator,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    fun list(
        after: UUID?,
        limit: Int,
    ): List<Revisioned<WebUser>> = users.list(after, limit)

    fun get(id: UUID): Revisioned<WebUser> = users.find(id) ?: throw NoSuchElementException("User $id does not exist")

    fun create(
        actor: Actor,
        username: String,
        email: String,
        password: String,
        roleId: String,
    ): Revisioned<WebUser> {
        owners.requireOwner(actor)
        requireRole(roleId)
        PasswordPolicy.validate(password)
        val user = WebUser(ids.next(), username, email, passwords.hash(password), roleId, clock.instant())
        users.create(user)
        return get(user.id)
    }

    fun update(
        actor: Actor,
        id: UUID,
        expectedVersion: Long,
        username: String,
        email: String,
        roleId: String,
        enabled: Boolean,
    ): Revisioned<WebUser> {
        val current = get(id).value
        owners.requireMayManage(actor, current)
        if (roleId != current.roleId) {
            owners.requireOwner(actor)
            requireRole(roleId)
        }
        transactions.executeWithoutResult {
            users.update(
                expectedVersion,
                current.copy(username = username, email = email, roleId = roleId, enabled = enabled),
            )
            if (roleId != current.roleId || enabled != current.enabled) logins.revokeUser(id)
        }
        return get(id)
    }

    fun resetPassword(
        actor: Actor,
        id: UUID,
        password: String,
    ) {
        owners.requireMayManage(actor, get(id).value)
        setPassword(id, password)
    }

    fun changeOwnPassword(
        actor: Actor,
        currentPassword: String,
        newPassword: String,
    ) {
        val user = self(actor)
        val hash = user.passwordHash ?: throw IllegalArgumentException("This account signs in with passkeys only")
        require(passwords.matches(currentPassword, hash)) { "The current password is wrong" }
        setPassword(user.id, newPassword)
    }

    fun revokeLogins(
        actor: Actor,
        id: UUID,
    ) {
        owners.requireMayManage(actor, get(id).value)
        logins.revokeUser(id)
    }

    fun delete(
        actor: Actor,
        id: UUID,
    ) {
        owners.requireMayManage(actor, get(id).value)
        transactions.executeWithoutResult {
            users.delete(id)
            passkeys.deleteAll(id)
            logins.revokeUser(id)
        }
    }

    /** The signed-in user; machine clients have no profile. */
    fun self(actor: Actor): WebUser {
        if (actor.type != ActorType.USER) throw AccessDeniedException("Only users have a profile")
        return get(UUID.fromString(actor.id)).value
    }

    private fun setPassword(
        id: UUID,
        password: String,
    ) {
        PasswordPolicy.validate(password)
        transactions.executeWithoutResult {
            users.setPasswordHash(id, passwords.hash(password))
            logins.revokeUser(id)
        }
    }

    private fun requireRole(roleId: String) {
        roles.find(roleId) ?: throw IllegalArgumentException("Role $roleId does not exist")
    }
}
