package io.ogwars.cloud.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ApiPermissionTest {
    @Test
    fun `scopes are unique and map back to their permission`() {
        ApiPermission.entries
            .map(ApiPermission::scope)
            .toSet()
            .size shouldBe ApiPermission.entries.size
        ApiPermission.entries.forEach { ApiPermission.fromScope(it.scope) shouldBe it }
        ApiPermission.fromScope("openid") shouldBe null
    }

    @Test
    fun `callers' permission lists must only name known permissions`() {
        ApiPermission.parse(listOf("groups:read", "groups:read")) shouldBe setOf(ApiPermission.GROUPS_READ)
        shouldThrow<IllegalArgumentException> { ApiPermission.parse(listOf("groups:read", "openid")) }
    }

    @Test
    fun `owners may do everything`() {
        BuiltInRole.OWNER.permissions shouldBe ApiPermission.entries.toSet()
    }

    @Test
    fun `admins may do everything except users, roles, OAuth clients and privacy`() {
        BuiltInRole.ADMIN.permissions shouldNotContainAnyOf
            listOf(
                ApiPermission.USERS_READ,
                ApiPermission.USERS_WRITE,
                ApiPermission.ROLES_READ,
                ApiPermission.ROLES_WRITE,
                ApiPermission.OAUTH_CLIENTS_READ,
                ApiPermission.OAUTH_CLIENTS_WRITE,
                ApiPermission.PRIVACY_EXPORT,
                ApiPermission.PRIVACY_ERASE,
            )
        BuiltInRole.ADMIN.permissions.size shouldBe ApiPermission.entries.size - OWNER_ONLY_PERMISSIONS
    }

    @Test
    fun `viewers may only read, which excludes exporting personal data`() {
        BuiltInRole.VIEWER.permissions.all(ApiPermission::readOnly) shouldBe true
        BuiltInRole.VIEWER.permissions.size shouldBe ApiPermission.entries.count { it.scope.endsWith(":read") }
        BuiltInRole.VIEWER.permissions shouldNotContainAnyOf listOf(ApiPermission.PRIVACY_EXPORT)
    }

    @Test
    fun `moderators handle servers and players`() {
        BuiltInRole.MODERATOR.permissions shouldContainExactlyInAnyOrder
            listOf(
                ApiPermission.OVERVIEW_READ,
                ApiPermission.SERVERS_READ,
                ApiPermission.SERVERS_COMMAND,
                ApiPermission.PLAYERS_READ,
                ApiPermission.PLAYERS_WRITE,
                ApiPermission.GROUPS_READ,
                ApiPermission.LOGS_READ,
            )
    }

    @Test
    fun `built-in roles are found by id`() {
        BuiltInRole.find("moderator") shouldBe BuiltInRole.MODERATOR
        BuiltInRole.find("builders") shouldBe null
    }

    @Test
    fun `web users need a sensible username and an email`() {
        shouldThrow<IllegalArgumentException> { user(username = "ab") }
        shouldThrow<IllegalArgumentException> { user(username = "owner@example.com") }
        shouldThrow<IllegalArgumentException> { user(email = "nobody") }
        user().username shouldBe "jevzo"
    }

    private fun user(
        username: String = "jevzo",
        email: String = "jevzo@example.com",
    ) = WebUser(UUID(0, 1), username, email, "{bcrypt}x", "owner", Instant.EPOCH)

    private companion object {
        const val OWNER_ONLY_PERMISSIONS = 8
    }
}
