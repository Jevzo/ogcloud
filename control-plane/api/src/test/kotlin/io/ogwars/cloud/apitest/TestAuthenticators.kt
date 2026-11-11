package io.ogwars.cloud.apitest

import io.ogwars.cloud.domain.user.Base32
import io.ogwars.cloud.domain.user.Totp
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.shouldBe
import java.time.Instant

/** Enrols authenticator apps through `/api/v1/me/totp` as a user would, for tests that need one. */
class TestAuthenticators(
    private val oauth: OAuthHttp,
    private val users: TestUsers,
) {
    private val totp = Totp()

    /** Enrols one for [user], checking a wrong first code is refused, and returns its secret. */
    fun enroll(user: WebUser): ByteArray {
        val token = users.token(user)
        val enrollment = oauth.json(oauth.call("POST", "$ME/totp", token, mapOf("password" to TestUsers.PASSWORD)))
        val secret = Base32.decode(enrollment["secret"] as String)
        oauth.call("POST", "$ME/totp/confirm", token, mapOf("code" to "000000")).statusCode() shouldBe BAD_REQUEST
        val current = totp.code(secret, totp.step(Instant.now()))
        oauth.call("POST", "$ME/totp/confirm", token, mapOf("code" to current)).statusCode() shouldBe NO_CONTENT
        return secret
    }

    /** The next step's code: newer than the one the enrolment used, and still accepted if a step boundary passes. */
    fun code(secret: ByteArray): String = totp.code(secret, totp.step(Instant.now()) + 1)

    private companion object {
        const val ME = "/api/v1/me"
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
    }
}
