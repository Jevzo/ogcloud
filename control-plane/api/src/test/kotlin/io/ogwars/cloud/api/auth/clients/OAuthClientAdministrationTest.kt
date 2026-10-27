package io.ogwars.cloud.api.auth.clients

import io.ogwars.cloud.api.auth.FirstPartyClients
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder

@ApiTest
class OAuthClientAdministrationTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val ownerToken = testUsers.token(testUsers.create(BuiltInRole.OWNER.id))

    @Test
    fun `owners create machine clients whose secret works once shown`() {
        val created = oauth.json(oauth.call("POST", CLIENTS, ownerToken, client(listOf("groups:read", "servers:read"))))
        val clientId = created["clientId"] as String

        oauth
            .clientCredentials(
                clientId,
                created["clientSecret"] as String,
                listOf("groups:read"),
            ).statusCode() shouldBe
            OK
        val listed = oauth.jsonList(oauth.call("GET", CLIENTS, ownerToken)).map { it as Map<*, *> }
        listed.single { it["clientId"] == clientId }["scopes"] shouldBe listOf("groups:read", "servers:read")
        listed.single { it["clientId"] == FirstPartyClients.WEB }["firstParty"] shouldBe true
    }

    @Test
    fun `scopes are set by owners only`() {
        val manager = testUsers.create(BuiltInRole.ADMIN.id)
        val managerToken = testUsers.token(manager, listOf(ApiPermission.OAUTH_CLIENTS_WRITE))

        oauth.call("POST", CLIENTS, managerToken, client(listOf("groups:read"))).statusCode() shouldBe FORBIDDEN
        oauth.call("POST", CLIENTS, ownerToken, client(listOf("not-a-permission"))).statusCode() shouldBe BAD_REQUEST
        oauth.call("POST", CLIENTS, ownerToken, client(emptyList())).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `a rotated secret replaces the old one`() {
        val created = oauth.json(oauth.call("POST", CLIENTS, ownerToken, client(listOf("groups:read"))))
        val clientId = created["clientId"] as String

        val rotated = oauth.json(oauth.call("POST", "$CLIENTS/$clientId/secret", ownerToken))

        oauth
            .clientCredentials(
                clientId,
                created["clientSecret"] as String,
                listOf("groups:read"),
            ).statusCode() shouldBe
            UNAUTHORIZED
        oauth
            .clientCredentials(
                clientId,
                rotated["clientSecret"] as String,
                listOf("groups:read"),
            ).statusCode() shouldBe
            OK
    }

    @Test
    fun `scope changes apply to the next token`() {
        val created = oauth.json(oauth.call("POST", CLIENTS, ownerToken, client(listOf("groups:read"))))
        val clientId = created["clientId"] as String

        oauth.call("PUT", "$CLIENTS/$clientId", ownerToken, client(listOf("npcs:read"))).statusCode() shouldBe OK

        oauth
            .clientCredentials(
                clientId,
                created["clientSecret"] as String,
                listOf("groups:read"),
            ).statusCode() shouldBe
            BAD_REQUEST
        oauth.clientCredentials(clientId, created["clientSecret"] as String, listOf("npcs:read")).statusCode() shouldBe
            OK
    }

    @Test
    fun `deleted clients get no tokens, and first-party apps cannot be deleted`() {
        val created = oauth.json(oauth.call("POST", CLIENTS, ownerToken, client(listOf("groups:read"))))
        val clientId = created["clientId"] as String

        oauth.call("DELETE", "$CLIENTS/$clientId", ownerToken).statusCode() shouldBe NO_CONTENT

        oauth
            .clientCredentials(
                clientId,
                created["clientSecret"] as String,
                listOf("groups:read"),
            ).statusCode() shouldBe
            UNAUTHORIZED
        oauth.call("DELETE", "$CLIENTS/${FirstPartyClients.WEB}", ownerToken).statusCode() shouldBe CONFLICT
    }

    private fun client(scopes: List<String>) = mapOf("name" to "CI pipeline", "scopes" to scopes)

    private companion object {
        const val CLIENTS = "/api/v1/oauth-clients"
        const val OK = 200
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val CONFLICT = 409
    }
}
