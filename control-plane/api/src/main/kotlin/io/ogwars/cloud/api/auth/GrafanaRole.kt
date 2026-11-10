package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser

/**
 * Grafana's role for a user (D-0185): owners administer it, readers of the overview look, everyone else is refused,
 * so is anyone whose effective permissions are empty, as an unlinked user's are (D-0094).
 */
object GrafanaRole {
    const val ADMIN = "Admin"
    const val VIEWER = "Viewer"

    fun of(
        user: WebUser,
        effective: Set<ApiPermission>,
    ): String? =
        when {
            effective.isEmpty() -> null
            user.roleId == BuiltInRole.OWNER.id -> ADMIN
            ApiPermission.OVERVIEW_READ in effective -> VIEWER
            else -> null
        }
}
