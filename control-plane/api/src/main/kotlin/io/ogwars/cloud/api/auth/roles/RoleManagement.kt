package io.ogwars.cloud.api.auth.roles

import io.ogwars.cloud.api.auth.Owners
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.Role
import org.springframework.stereotype.Service

/** Custom roles; only owners shape permissions (D-0094). Holders pick up a change with their next token. */
@Service
class RoleManagement(
    private val roles: RoleStore,
    private val owners: Owners,
) {
    fun list(): List<Revisioned<Role>> = roles.findAll()

    fun create(
        actor: Actor,
        id: String,
        name: String,
        permissions: Collection<String>,
    ): Revisioned<Role> {
        owners.requireOwner(actor)
        roles.create(Role(id, name, ApiPermission.parse(permissions)))
        return get(id)
    }

    fun update(
        actor: Actor,
        id: String,
        expectedVersion: Long,
        name: String,
        permissions: Collection<String>,
    ): Revisioned<Role> {
        owners.requireOwner(actor)
        val current = get(id).value
        roles.update(expectedVersion, current.copy(name = name, permissions = ApiPermission.parse(permissions)))
        return get(id)
    }

    fun delete(
        actor: Actor,
        id: String,
    ) {
        owners.requireOwner(actor)
        roles.delete(id)
    }

    private fun get(id: String) = roles.find(id) ?: throw NoSuchElementException("Role $id does not exist")
}
