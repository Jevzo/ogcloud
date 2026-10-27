package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JdbcWebUserStore(
    private val jdbc: JdbcClient,
) : WebUserStore {
    override fun find(id: UUID): WebUser? =
        jdbc
            .sql("select * from web_users where id = :id")
            .param("id", id)
            .query(WEB_USER)
            .optional()
            .orElse(null)

    override fun findByLogin(login: String): WebUser? =
        jdbc
            .sql("select * from web_users where lower(username) = lower(:login) or lower(email) = lower(:login)")
            .param("login", login)
            .query(WEB_USER)
            .optional()
            .orElse(null)

    override fun create(user: WebUser) {
        jdbc
            .sql(
                """
                insert into web_users (id, username, email, password_hash, role_id, linked_player_uuid, enabled, created_at,
                    updated_at)
                values (:id, :username, :email, :passwordHash, :roleId, :linkedPlayerUuid, :enabled, :createdAt, :createdAt)
                """.trimIndent(),
            ).param("id", user.id)
            .param("username", user.username)
            .param("email", user.email)
            .param("passwordHash", user.passwordHash)
            .param("roleId", user.roleId)
            .param("linkedPlayerUuid", user.linkedPlayerUuid)
            .param("enabled", user.enabled)
            .param("createdAt", user.createdAt.toTimestamp())
            .update()
    }

    private companion object {
        val WEB_USER =
            RowMapper { rs, _ ->
                WebUser(
                    id = rs.uuid("id"),
                    username = rs.getString("username"),
                    email = rs.getString("email"),
                    passwordHash = rs.getString("password_hash"),
                    roleId = rs.getString("role_id"),
                    createdAt = rs.instant("created_at"),
                    linkedPlayerUuid = rs.uuidOrNull("linked_player_uuid"),
                    enabled = rs.getBoolean("enabled"),
                )
            }
    }
}

@Repository
class JdbcRoleStore(
    private val jdbc: JdbcClient,
) : RoleStore {
    // Permissions dropped from the catalog may linger in old rows; they grant nothing.
    override fun customPermissions(roleId: String): Set<ApiPermission> =
        jdbc
            .sql("select permission from role_permissions where role_id = :roleId")
            .param("roleId", roleId)
            .query { rs, _ -> ApiPermission.fromScope(rs.getString("permission")) }
            .list()
            .filterNotNull()
            .toSet()
}
