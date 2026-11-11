package io.ogwars.cloud.api.auth

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.session.data.redis.RedisIndexedSessionRepository
import java.util.UUID

/** Only the sign-in paths read or extend a login session (D-0220), so the dashboard's calls never keep one alive. */
@ApiTest
class SessionScopeTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val sessions: RedisIndexedSessionRepository,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)

    @Test
    fun `API calls carrying the session cookie leave the login session as it was`() {
        val user = testUsers.create()
        val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"
        val browser = oauth.signInBrowser(FirstPartyClients.WEB, CALLBACK, user.username, TestUsers.PASSWORD, verifier)
        val token = oauth.exchange(FirstPartyClients.WEB, oauth.codeFrom(browser.callback), CALLBACK, verifier)
        val before = sessions.findById(browser.sessionId)?.lastAccessedTime.shouldNotBeNull()

        browser.call("/api/v1/me", token.accessToken).statusCode() shouldBe OK

        sessions.findById(browser.sessionId)?.lastAccessedTime shouldBe before
    }

    private companion object {
        const val CALLBACK = "$TEST_ISSUER/auth/callback"
        const val OK = 200
    }
}
