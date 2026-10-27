package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.auth.FirstPartyClients
import io.ogwars.cloud.api.auth.TokenClaims
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TestSigningKeys
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import java.util.UUID

@ApiTest
class UserAdministrationTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val sessions: FindByIndexNameSessionRepository<out Session>,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val decoder: JwtDecoder,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val owner = testUsers.create(BuiltInRole.OWNER.id)
    private val ownerToken = testUsers.token(owner)

    /** Holds users:write without being an owner, through a custom role. */
    private val manager = testUsers.create(BuiltInRole.ADMIN.id)
    private val managerToken =
        testUsers.token(
            manager,
            BuiltInRole.ADMIN.permissions + ApiPermission.USERS_WRITE + ApiPermission.USERS_READ,
        )

    @Test
    fun `owners create users, and the new user can sign in`() {
        val created = oauth.call("POST", USERS, ownerToken, newUser("fresh-${suffix()}", BuiltInRole.MODERATOR.id))

        created.statusCode() shouldBe CREATED
        val id = oauth.json(created)["id"] as String
        oauth.json(oauth.call("GET", "$USERS/$id", ownerToken))["roleId"] shouldBe BuiltInRole.MODERATOR.id
        signIn(oauth.json(created)["username"] as String, PASSWORD).accessToken.isNotEmpty() shouldBe true
    }

    @Test
    fun `assigning roles is for owners only`() {
        val target = testUsers.create()

        oauth
            .call(
                "POST",
                USERS,
                managerToken,
                newUser("nope-${suffix()}", BuiltInRole.VIEWER.id),
            ).statusCode() shouldBe
            FORBIDDEN
        oauth
            .call(
                "PUT",
                "$USERS/${target.id}",
                managerToken,
                update(target, roleId = BuiltInRole.ADMIN.id),
            ).statusCode() shouldBe
            FORBIDDEN
        oauth
            .call(
                "PUT",
                "$USERS/${target.id}",
                managerToken,
                update(target, email = "renamed-${suffix()}@example.com"),
            ).statusCode() shouldBe
            OK
    }

    @Test
    fun `only owners touch owners`() {
        val other = testUsers.create(BuiltInRole.OWNER.id)

        oauth.call("PUT", "$USERS/${other.id}", managerToken, update(other, enabled = false)).statusCode() shouldBe
            FORBIDDEN
        oauth
            .call(
                "PUT",
                "$USERS/${other.id}/password",
                managerToken,
                mapOf("password" to NEW_PASSWORD),
            ).statusCode() shouldBe
            FORBIDDEN
        oauth.call("DELETE", "$USERS/${other.id}", managerToken).statusCode() shouldBe FORBIDDEN
        oauth.call("DELETE", "$USERS/${other.id}", ownerToken).statusCode() shouldBe NO_CONTENT
    }

    @Test
    fun `a role change ends the user's logins and login sessions`() {
        val target = testUsers.create()
        val tokens = signIn(target.username, PASSWORD)
        sessions.findByPrincipalName(target.id.toString()).keys.shouldNotBeEmpty()

        oauth
            .call(
                "PUT",
                "$USERS/${target.id}",
                ownerToken,
                update(target, roleId = BuiltInRole.MODERATOR.id),
            ).statusCode() shouldBe
            OK

        oauth.error(oauth.refresh(FirstPartyClients.WEB, tokens.refreshToken ?: error("no refresh token"))) shouldBe
            "invalid_grant"
        sessions.findByPrincipalName(target.id.toString()).keys.shouldBeEmpty()
    }

    @Test
    fun `stale updates and weak passwords are refused`() {
        val target = testUsers.create()
        oauth
            .call(
                "PUT",
                "$USERS/${target.id}",
                ownerToken,
                update(target, email = "first-${suffix()}@example.com"),
            ).statusCode() shouldBe
            OK

        oauth
            .call(
                "PUT",
                "$USERS/${target.id}",
                ownerToken,
                update(target, email = "second-${suffix()}@example.com"),
            ).statusCode() shouldBe
            CONFLICT
        oauth
            .call(
                "PUT",
                "$USERS/${target.id}/password",
                ownerToken,
                mapOf("password" to "short"),
            ).statusCode() shouldBe
            BAD_REQUEST
    }

    @Test
    fun `a password reset works for the next sign-in`() {
        val target = testUsers.create()

        oauth
            .call(
                "PUT",
                "$USERS/${target.id}/password",
                managerToken,
                mapOf("password" to NEW_PASSWORD),
            ).statusCode() shouldBe
            NO_CONTENT

        signIn(target.username, NEW_PASSWORD).accessToken.isNotEmpty() shouldBe true
    }

    @Test
    fun `users are listed in pages`() {
        repeat(2) { testUsers.create() }

        val first = oauth.json(oauth.call("GET", "$USERS?limit=1", ownerToken))
        val second = oauth.json(oauth.call("GET", "$USERS?limit=1&cursor=${first["nextCursor"]}", ownerToken))

        (first["items"] as List<*>).size shouldBe 1
        ((second["items"] as List<*>).single() as Map<*, *>)["id"] shouldBe
            ((oauth.json(oauth.call("GET", "$USERS?limit=2", ownerToken))["items"] as List<*>)[1] as Map<*, *>)["id"]
    }

    @Test
    fun `reading users needs users read`() {
        oauth.call("GET", USERS, testUsers.token(testUsers.create(BuiltInRole.MODERATOR.id))).statusCode() shouldBe
            FORBIDDEN
    }

    @Test
    fun `users see and change their own profile`() {
        val user = testUsers.create(BuiltInRole.MODERATOR.id)
        val token = testUsers.token(user)

        val me = oauth.json(oauth.call("GET", ME, token))
        me["id"] shouldBe user.id.toString()
        me["permissions"] shouldBe
            BuiltInRole.MODERATOR.permissions
                .map { it.scope }
                .sorted()

        oauth.call("PUT", "$ME/password", token, changePassword("wrong password!!")).statusCode() shouldBe BAD_REQUEST
        oauth.call("PUT", "$ME/password", token, changePassword(PASSWORD)).statusCode() shouldBe NO_CONTENT
        oauth.call("GET", ME, machineToken()).statusCode() shouldBe FORBIDDEN
    }

    @Test
    fun `custom roles are built by owners and reach their holders`() {
        val roleId = "builders-${suffix()}"
        val created =
            oauth.call(
                "POST",
                ROLES,
                ownerToken,
                mapOf(
                    "id" to roleId,
                    "name" to "Builders",
                    "permissions" to listOf("templates:write"),
                ),
            )
        created.statusCode() shouldBe CREATED
        val holder = testUsers.create(roleId)

        oauth.json(oauth.call("GET", ME, testUsers.token(holder)))["permissions"] shouldBe listOf("templates:write")
        signInPermissions(holder) shouldContainExactly listOf("templates:write")
        oauth.call("DELETE", "$ROLES/$roleId", ownerToken).statusCode() shouldBe CONFLICT
    }

    @Test
    fun `roles are for owners only and built-in roles stay as they are`() {
        val rolesManager = testUsers.token(manager, listOf(ApiPermission.ROLES_READ, ApiPermission.ROLES_WRITE))

        oauth
            .call(
                "POST",
                ROLES,
                rolesManager,
                mapOf(
                    "id" to "x-${suffix()}",
                    "name" to "X",
                    "permissions" to emptyList<String>(),
                ),
            ).statusCode() shouldBe FORBIDDEN
        oauth
            .call(
                "PUT",
                "$ROLES/owner",
                ownerToken,
                mapOf(
                    "name" to "Boss",
                    "permissions" to emptyList<String>(),
                    "version" to 0,
                ),
            ).statusCode() shouldBe CONFLICT
        oauth
            .call(
                "POST",
                ROLES,
                ownerToken,
                mapOf(
                    "id" to "y-${suffix()}",
                    "name" to "Y",
                    "permissions" to listOf("everything"),
                ),
            ).statusCode() shouldBe BAD_REQUEST
        oauth.jsonList(oauth.call("GET", "$ROLES/permissions", ownerToken)).size shouldBe ApiPermission.entries.size
    }

    @Test
    fun `while a Minecraft link is required, unlinked users hold no permissions`() {
        val user = testUsers.create(BuiltInRole.ADMIN.id)
        setRequireLink(true)
        try {
            oauth.json(oauth.call("GET", ME, testUsers.token(user)))["permissions"] shouldBe emptyList<String>()
            signInPermissions(user).shouldBeEmpty()
        } finally {
            setRequireLink(false)
        }
    }

    @Test
    fun `handlers that declare no permission are refused`() {
        oauth.call("GET", "/api/v1/probes/undeclared", ownerToken).statusCode() shouldBe FORBIDDEN
    }

    private fun signIn(
        username: String,
        password: String,
    ): OAuthHttp.Tokens {
        val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"
        val callback = oauth.authorize(FirstPartyClients.WEB, WEB_REDIRECT, username, password, verifier)
        return oauth.exchange(FirstPartyClients.WEB, oauth.codeFrom(callback), WEB_REDIRECT, verifier)
    }

    private fun signInPermissions(user: WebUser): List<String> =
        decoder
            .decode(
                signIn(user.username, PASSWORD).accessToken,
            ).getClaimAsStringList(TokenClaims.PERMISSIONS_CLAIM)
            .orEmpty()

    private fun machineToken() =
        TestSigningKeys.accessToken(TestSigningKeys.key(TestSigningKeys.CURRENT), listOf("users:read"))

    private fun setRequireLink(required: Boolean) {
        jdbc.sql("update network_settings set require_minecraft_link = :required").param("required", required).update()
    }

    private fun newUser(
        username: String,
        roleId: String,
    ) = mapOf("username" to username, "email" to "$username@example.com", "password" to PASSWORD, "roleId" to roleId)

    private fun update(
        user: WebUser,
        roleId: String = user.roleId,
        email: String = user.email,
        enabled: Boolean = user.enabled,
    ) = mapOf("username" to user.username, "email" to email, "roleId" to roleId, "enabled" to enabled, "version" to 0)

    private fun changePassword(current: String) = mapOf("currentPassword" to current, "newPassword" to NEW_PASSWORD)

    private fun suffix() = UUID.randomUUID().toString().take(SUFFIX)

    private companion object {
        const val USERS = "/api/v1/users"
        const val ROLES = "/api/v1/roles"
        const val ME = "/api/v1/me"
        const val WEB_REDIRECT = "$TEST_ISSUER/auth/callback"
        const val PASSWORD = TestUsers.PASSWORD
        const val NEW_PASSWORD = "another long password"
        const val SUFFIX = 8
        const val OK = 200
        const val CREATED = 201
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val CONFLICT = 409
    }
}
