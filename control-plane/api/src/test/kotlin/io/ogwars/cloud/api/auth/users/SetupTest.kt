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

@ApiTest
class SetupTest(
    @LocalServerPort port: Int,
    @Autowired private val jdbc: JdbcClient,
) {
    private val oauth = OAuthHttp(port)

    @Test
    fun `the setup token creates the first owner exactly once`() {
        // Other API tests leave owners behind; setup only runs on an ownerless installation.
        jdbc.sql("delete from web_users where role_id = :owner").param("owner", BuiltInRole.OWNER.id).update()
        oauth.json(oauth.call("GET", SETUP, null))["pending"] shouldBe true

        oauth.call("POST", SETUP, null, request("not-the-token")).statusCode() shouldBe UNAUTHORIZED
        oauth.call("POST", SETUP, null, request(TEST_SETUP_TOKEN, password = "short")).statusCode() shouldBe BAD_REQUEST
        val created = oauth.call("POST", SETUP, null, request(TEST_SETUP_TOKEN))

        created.statusCode() shouldBe CREATED
        oauth.json(created)["roleId"] shouldBe BuiltInRole.OWNER.id
        oauth.json(oauth.call("GET", SETUP, null))["pending"] shouldBe false
        oauth.call("POST", SETUP, null, request(TEST_SETUP_TOKEN, username = "second")).statusCode() shouldBe CONFLICT
    }

    private fun request(
        token: String,
        username: String = "first-owner",
        password: String = "a long enough password",
    ) = mapOf("setupToken" to token, "username" to username, "email" to "$username@example.com", "password" to password)

    private companion object {
        const val SETUP = "/api/v1/setup"
        const val CREATED = 201
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val CONFLICT = 409
    }
}
