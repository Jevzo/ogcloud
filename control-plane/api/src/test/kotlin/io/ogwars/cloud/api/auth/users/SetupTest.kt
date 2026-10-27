package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_SETUP_TOKEN
import io.ogwars.cloud.domain.user.BuiltInRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

@ApiTest
class SetupTest(
    @LocalServerPort port: Int,
    @Autowired private val jdbc: JdbcClient,
) {
    private val oauth = OAuthHttp(port)

    @Test
    fun `the setup token creates the first owner exactly once`() {
        // Setup only runs on an ownerless installation: other tests' owners step aside and come back afterwards.
        val owners =
            jdbc
                .sql("update web_users set role_id = :viewer where role_id = :owner returning id")
                .param("viewer", BuiltInRole.VIEWER.id)
                .param("owner", BuiltInRole.OWNER.id)
                .query(UUID::class.java)
                .list()
        val username = "first-${UUID.randomUUID().toString().take(SUFFIX)}"
        try {
            oauth.json(oauth.call("GET", SETUP, null))["pending"] shouldBe true

            oauth.call("POST", SETUP, null, request("not-the-token", username)).statusCode() shouldBe UNAUTHORIZED
            oauth
                .call(
                    "POST",
                    SETUP,
                    null,
                    request(TEST_SETUP_TOKEN, username, password = "short"),
                ).statusCode() shouldBe
                BAD_REQUEST
            val created = oauth.call("POST", SETUP, null, request(TEST_SETUP_TOKEN, username))

            created.statusCode() shouldBe CREATED
            oauth.json(created)["roleId"] shouldBe BuiltInRole.OWNER.id
            oauth.json(oauth.call("GET", SETUP, null))["pending"] shouldBe false
            oauth.call("POST", SETUP, null, request(TEST_SETUP_TOKEN, "$username-2")).statusCode() shouldBe CONFLICT
        } finally {
            jdbc.sql("delete from web_users where username = :username").param("username", username).update()
            if (owners.isNotEmpty()) {
                jdbc
                    .sql("update web_users set role_id = :owner where id in (:ids)")
                    .param("owner", BuiltInRole.OWNER.id)
                    .param("ids", owners)
                    .update()
            }
        }
    }

    private fun request(
        token: String,
        username: String,
        password: String = "a long enough password",
    ) = mapOf("setupToken" to token, "username" to username, "email" to "$username@example.com", "password" to password)

    private companion object {
        const val SETUP = "/api/v1/setup"
        const val SUFFIX = 8
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val CONFLICT = 409
    }
}
