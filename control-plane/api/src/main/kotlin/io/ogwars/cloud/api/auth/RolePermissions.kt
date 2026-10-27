package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.stereotype.Component

@Component
class RolePermissions(
    private val roles: RoleStore,
) {
    fun of(user: WebUser): Set<ApiPermission> =
        BuiltInRole.find(user.roleId)?.permissions ?: roles.customPermissions(user.roleId)
}
