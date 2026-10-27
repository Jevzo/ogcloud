package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.store.NetworkSettingsStore
import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.stereotype.Component

/** What a user may do right now: their role's permissions, or none while a required Minecraft link is missing (D-0094). */
@Component
class UserPermissions(
    private val roles: RoleStore,
    private val settings: NetworkSettingsStore,
) {
    fun of(user: WebUser): Set<ApiPermission> {
        if (user.linkedPlayerUuid == null && settings.load().value.requireMinecraftLink) return emptySet()
        return BuiltInRole.find(user.roleId)?.permissions ?: roles
            .find(user.roleId)
            ?.value
            ?.permissions
            .orEmpty()
    }
}
