package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.api.auth.FirstPartyClients
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TEST_TOTP_KEY_ID
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.Base32
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.Totp
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

@ApiTest
class MfaTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val jdbc: JdbcClient,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val totp = Totp()

    @Test
    fun `an enrolled authenticator is asked for after the password, and a code works once`() {
        val user = testUsers.create()
        val secret = enroll(user)

        authorize(user, totpCode = null).path shouldBe OAuthHttp.MFA_PAGE
        val code = code(secret)
        authorize(user) { code }.toString() shouldStartWith WEB_REDIRECT
        authorize(user) { code }.query shouldContain "error"
    }

    @Test
    fun `a wrong code sends the user back`() {
        val user = testUsers.create()
        enroll(user)

        authorize(user) { "000000" }.query shouldContain "error"
    }

    @Test
    fun `an owner without a second factor enrolls one before getting a code`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)

        authorize(owner, totpCode = null).path shouldBe OAuthHttp.MFA_PAGE
        val callback =
            oauth.enrollDuringLogin(
                FirstPartyClients.WEB,
                WEB_REDIRECT,
                owner.username,
                TestUsers.PASSWORD,
                verifier,
            ) { secret ->
                code(Base32.decode(secret))
            }

        callback.toString() shouldStartWith WEB_REDIRECT
        oauth
            .exchange(
                FirstPartyClients.WEB,
                oauth.codeFrom(callback),
                WEB_REDIRECT,
                verifier,
            ).accessToken
            .shouldNotBeNull()
    }

    @Test
    fun `secrets are stored encrypted under the active key`() {
        val user = testUsers.create()
        val secret = enroll(user)

        val stored =
            jdbc
                .sql("select key_id, secret_encrypted from web_user_totp where user_id = :id")
                .param("id", user.id)
                .query { rs, _ -> rs.getString("key_id") to rs.getBytes("secret_encrypted") }
                .single()
        stored.first shouldBe TEST_TOTP_KEY_ID
        stored.second
            .toList()
            .windowed(secret.size)
            .none { it == secret.toList() } shouldBe true
    }

    @Test
    fun `an authenticator is replaced only after removing it`() {
        val user = testUsers.create()
        enroll(user)
        val token = testUsers.token(user)

        oauth.call("POST", "$ME/totp", token).statusCode() shouldBe CONFLICT
        oauth.json(oauth.call("GET", ME, token))["totpEnabled"] shouldBe true
        oauth.call("DELETE", "$ME/totp", token).statusCode() shouldBe NO_CONTENT
        oauth.json(oauth.call("GET", ME, token))["totpEnabled"] shouldBe false
    }

    @Test
    fun `the login page can ask for passkey options`() {
        val options = oauth.passkeyOptions()

        options.statusCode() shouldBe OK
        oauth.json(options)["rpId"] shouldBe "ogcloud.test"
    }

    /** Enrolls an authenticator through the API and returns its secret. */
    private fun enroll(user: WebUser): ByteArray {
        val token = testUsers.token(user)
        val enrollment = oauth.json(oauth.call("POST", "$ME/totp", token))
        val secret = Base32.decode(enrollment["secret"] as String)
        oauth.call("POST", "$ME/totp/confirm", token, mapOf("code" to "000000")).statusCode() shouldBe BAD_REQUEST
        // The confirmation uses the previous step, leaving the current one for the next sign-in.
        val previous = totp.code(secret, totp.step(Instant.now()) - 1)
        oauth.call("POST", "$ME/totp/confirm", token, mapOf("code" to previous)).statusCode() shouldBe NO_CONTENT
        return secret
    }

    private fun code(secret: ByteArray) = totp.code(secret, totp.step(Instant.now()))

    private val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"

    private fun authorize(
        user: WebUser,
        totpCode: (() -> String)?,
    ) = oauth.authorize(FirstPartyClients.WEB, WEB_REDIRECT, user.username, TestUsers.PASSWORD, verifier, totpCode)

    private companion object {
        const val ME = "/api/v1/me"
        const val WEB_REDIRECT = "$TEST_ISSUER/auth/callback"
        const val OK = 200
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val CONFLICT = 409
    }
}
