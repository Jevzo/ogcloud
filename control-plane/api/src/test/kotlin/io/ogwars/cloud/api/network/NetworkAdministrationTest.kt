package io.ogwars.cloud.api.network

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder

@ApiTest
class NetworkAdministrationTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val groups: GroupStore,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val admin = testUsers.token(testUsers.create(BuiltInRole.ADMIN.id))

    // The seeded settings name "lobby" as the default group, and updates check that it exists.
    @BeforeEach
    fun defaultGroup() {
        try {
            groups.create(group(id = "lobby"))
        } catch (_: ConflictException) {
            // Another test created it already.
        }
    }

    @Test
    fun `settings change against their version and keep the EULA acceptance`() {
        val motd = "&aWelcome ${System.nanoTime()}"
        val changed = update(mapOf("motd" to motd))

        oauth.call("PUT", NETWORK, admin, changed).statusCode() shouldBe OK
        oauth.call("PUT", NETWORK, admin, changed).statusCode() shouldBe CONFLICT
        fields()["motd"] shouldBe motd
    }

    @Test
    fun `the default group must exist`() {
        oauth.call("PUT", NETWORK, admin, update(mapOf("defaultGroupId" to "nowhere"))).statusCode() shouldBe
            BAD_REQUEST
    }

    @Test
    fun `only owners accept the EULA, once`() {
        val owner = testUsers.token(testUsers.create(BuiltInRole.OWNER.id))

        oauth.call("POST", "$NETWORK/eula", admin).statusCode() shouldBe FORBIDDEN
        val accepted = oauth.json(oauth.call("POST", "$NETWORK/eula", owner))["eulaAcceptedAt"].shouldNotBeNull()
        oauth.json(oauth.call("POST", "$NETWORK/eula", owner))["eulaAcceptedAt"] shouldBe accepted
    }

    @Test
    fun `a network restart needs maintenance, a rotation does not`() {
        oauth.call("POST", "$NETWORK/restart", admin, mapOf("batchSize" to null)).statusCode() shouldBe CONFLICT

        val rotation = oauth.call("POST", "$NETWORK/forwarding/rotate", admin)
        rotation.statusCode() shouldBe ACCEPTED
        oauth.json(rotation)["kind"] shouldBe "forwarding.rotate"

        oauth.call("PUT", NETWORK, admin, update(mapOf("maintenance" to true))).statusCode() shouldBe OK
        try {
            oauth.json(oauth.call("POST", "$NETWORK/restart", admin, mapOf("batchSize" to null)))["kind"] shouldBe
                "network.restart"
        } finally {
            oauth.call("PUT", NETWORK, admin, update(mapOf("maintenance" to false)))
        }
    }

    @Test
    fun `reading needs network read`() {
        val moderator = testUsers.token(testUsers.create(BuiltInRole.MODERATOR.id))
        val reader = testUsers.token(testUsers.create(), listOf(ApiPermission.NETWORK_READ))

        oauth.call("GET", NETWORK, moderator).statusCode() shouldBe FORBIDDEN
        oauth.call("GET", NETWORK, reader).statusCode() shouldBe OK
    }

    private fun fields(): Map<*, *> = oauth.json(oauth.call("GET", NETWORK, admin))["settings"] as Map<*, *>

    /** The current settings with [changes], against the current version. */
    private fun update(changes: Map<String, Any?>): Map<String, Any?> {
        val current = oauth.json(oauth.call("GET", NETWORK, admin))
        return mapOf("settings" to (current["settings"] as Map<*, *>) + changes, "version" to current["version"])
    }

    private companion object {
        const val NETWORK = "/api/v1/network"
        const val OK = 200
        const val ACCEPTED = 202
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val CONFLICT = 409
    }
}
