package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.store.StaleUpdateException
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.Role
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Repository
class JdbcWebUserStore(
    private val jdbc: JdbcClient,
    private val clock: Clock,
) : WebUserStore {
    override fun find(id: UUID): Revisioned<WebUser>? =
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
            .map { it.value }
            .orElse(null)

    override fun findByLinkedPlayer(playerUuid: UUID): Revisioned<WebUser>? =
        jdbc
            .sql("select * from web_users where linked_player_uuid = :player")
            .param("player", playerUuid)
            .query(WEB_USER)
            .optional()
            .orElse(null)

    override fun list(
        after: UUID?,
        limit: Int,
    ): List<Revisioned<WebUser>> =
        jdbc
            .sql("select * from web_users where id > :after order by id limit :limit")
            .param("after", after ?: MIN_ID)
            .param("limit", limit)
            .query(WEB_USER)
            .list()

    override fun create(user: WebUser) {
        unique {
            jdbc
                .sql(
                    """
                    insert into web_users (id, username, email, password_hash, role_id, linked_player_uuid, enabled,
                        created_at, updated_at)
                    values (:id, :username, :email, :passwordHash, :roleId, :linkedPlayerUuid, :enabled, :createdAt,
                        :createdAt)
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
    }

    // The advisory lock makes concurrent setups on several API replicas take turns.
    @Transactional
    override fun createFirstOwner(owner: WebUser): Boolean {
        require(owner.isOwner) { "The first user must be an owner" }
        jdbc
            .sql("select pg_advisory_xact_lock(:key)")
            .param("key", SETUP_LOCK)
            .query()
            .singleRow()
        if (ownerExists()) return false
        create(owner)
        return true
    }

    override fun ownerExists(): Boolean =
        jdbc
            .sql("select exists(select 1 from web_users where role_id = :owner)")
            .param("owner", BuiltInRole.OWNER.id)
            .query(Boolean::class.java)
            .single()

    @Transactional
    override fun update(
        expectedVersion: Long,
        user: WebUser,
    ) {
        if (!(user.isOwner && user.enabled)) keepAnotherOwner(user.id)
        val updated =
            unique {
                jdbc
                    .sql(
                        """
                        update web_users set username = :username, email = :email, role_id = :roleId,
                            linked_player_uuid = :linkedPlayerUuid, enabled = :enabled, updated_at = :now,
                            version = version + 1
                        where id = :id and version = :version
                        """.trimIndent(),
                    ).param("id", user.id)
                    .param("username", user.username)
                    .param("email", user.email)
                    .param("roleId", user.roleId)
                    .param("linkedPlayerUuid", user.linkedPlayerUuid)
                    .param("enabled", user.enabled)
                    .param("now", clock.instant().toTimestamp())
                    .param("version", expectedVersion)
                    .update()
            }
        if (updated == 0) {
            find(user.id) ?: throw NoSuchElementException("User ${user.id} does not exist")
            throw StaleUpdateException("User ${user.id} changed since version $expectedVersion")
        }
    }

    override fun setPasswordHash(
        id: UUID,
        passwordHash: String,
    ) {
        val updated =
            jdbc
                .sql(
                    "update web_users set password_hash = :hash, updated_at = :now, version = version + 1 where id = :id",
                ).param("id", id)
                .param("hash", passwordHash)
                .param("now", clock.instant().toTimestamp())
                .update()
        if (updated == 0) throw NoSuchElementException("User $id does not exist")
    }

    @Transactional
    override fun delete(id: UUID) {
        keepAnotherOwner(id)
        if (jdbc.sql("delete from web_users where id = :id").param("id", id).update() == 0) {
            throw NoSuchElementException("User $id does not exist")
        }
    }

    // Locking every enabled owner makes concurrent demotions take turns, so two cannot remove the last two owners.
    private fun keepAnotherOwner(leaving: UUID) {
        val owners =
            jdbc
                .sql("select id from web_users where role_id = :owner and enabled for update")
                .param("owner", BuiltInRole.OWNER.id)
                .query(UUID::class.java)
                .list()
        if (owners ==
            listOf(leaving)
        ) {
            throw ConflictException("The last enabled owner cannot be disabled, demoted or deleted")
        }
    }

    private fun <T> unique(write: () -> T): T =
        try {
            write()
        } catch (e: DuplicateKeyException) {
            val message =
                if (LINKED_PLAYER_CONSTRAINT in e.mostSpecificCause.message.orEmpty()) {
                    "The Minecraft account is linked to another user"
                } else {
                    "The username or email is already in use"
                }
            throw ConflictException(message).apply { initCause(e) }
        }

    private companion object {
        const val LINKED_PLAYER_CONSTRAINT = "web_users_linked_player_uuid_key"

        /** Arbitrary, but fixed: every replica must use the same key. */
        const val SETUP_LOCK = 0x6f67636c6f7564L

        val WEB_USER =
            RowMapper { rs, _ ->
                Revisioned(
                    WebUser(
                        id = rs.uuid("id"),
                        username = rs.getString("username"),
                        email = rs.getString("email"),
                        passwordHash = rs.getString("password_hash"),
                        roleId = rs.getString("role_id"),
                        createdAt = rs.instant("created_at"),
                        linkedPlayerUuid = rs.uuidOrNull("linked_player_uuid"),
                        enabled = rs.getBoolean("enabled"),
                    ),
                    rs.getLong("version"),
                )
            }
    }
}

@Repository
class JdbcRoleStore(
    private val jdbc: JdbcClient,
    private val clock: Clock,
) : RoleStore {
    override fun findAll(): List<Revisioned<Role>> =
        jdbc.sql("$SELECT group by r.id order by r.built_in desc, r.id").query(ROLE).list()

    override fun find(id: String): Revisioned<Role>? =
        jdbc
            .sql("$SELECT where r.id = :id group by r.id")
            .param("id", id)
            .query(ROLE)
            .optional()
            .orElse(null)

    @Transactional
    override fun create(role: Role) {
        require(!role.builtIn) { "Built-in roles come from the migrations" }
        try {
            jdbc
                .sql("insert into roles (id, name, created_at, updated_at) values (:id, :name, :now, :now)")
                .param("id", role.id)
                .param("name", role.name)
                .param("now", clock.instant().toTimestamp())
                .update()
        } catch (e: DuplicateKeyException) {
            throw ConflictException("Role ${role.id} already exists").apply { initCause(e) }
        }
        insertPermissions(role)
    }

    @Transactional
    override fun update(
        expectedVersion: Long,
        role: Role,
    ) {
        val existing = find(role.id) ?: throw NoSuchElementException("Role ${role.id} does not exist")
        if (existing.value.builtIn) throw ConflictException("Built-in roles cannot be changed")
        val updated =
            jdbc
                .sql(
                    "update roles set name = :name, updated_at = :now, version = version + 1 where id = :id and version = :version",
                ).param("id", role.id)
                .param("name", role.name)
                .param("now", clock.instant().toTimestamp())
                .param("version", expectedVersion)
                .update()
        if (updated == 0) throw StaleUpdateException("Role ${role.id} changed since version $expectedVersion")
        jdbc.sql("delete from role_permissions where role_id = :id").param("id", role.id).update()
        insertPermissions(role)
    }

    @Transactional
    override fun delete(id: String) {
        val existing = find(id) ?: throw NoSuchElementException("Role $id does not exist")
        if (existing.value.builtIn) throw ConflictException("Built-in roles cannot be deleted")
        val holders =
            jdbc
                .sql("select count(*) from web_users where role_id = :id")
                .param("id", id)
                .query(Long::class.java)
                .single()
        if (holders > 0) throw ConflictException("Role $id is assigned to $holders users")
        jdbc.sql("delete from roles where id = :id").param("id", id).update()
    }

    private fun insertPermissions(role: Role) {
        role.permissions.forEach { permission ->
            jdbc
                .sql("insert into role_permissions (role_id, permission) values (:id, :permission)")
                .param("id", role.id)
                .param("permission", permission.scope)
                .update()
        }
    }

    private companion object {
        const val SELECT =
            "select r.id, r.name, r.built_in, r.version, " +
                "array_remove(array_agg(p.permission), null) as permissions " +
                "from roles r left join role_permissions p on p.role_id = r.id"

        // Permissions dropped from the catalog may linger in old rows; they grant nothing.
        val ROLE =
            RowMapper { rs, _ ->
                val id = rs.getString("id")
                val builtIn = rs.getBoolean("built_in")
                val stored =
                    (
                        rs
                            .getArray(
                                "permissions",
                            ).array as Array<*>
                    ).mapNotNull { ApiPermission.fromScope(it as String) }
                Revisioned(
                    Role(id, rs.getString("name"), BuiltInRole.find(id)?.permissions ?: stored.toSet(), builtIn),
                    rs.getLong("version"),
                )
            }
    }
}
