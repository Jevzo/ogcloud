package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.auth.FirstPartyClients
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TestAuthenticators
import io.ogwars.cloud.apitest.TestPasskeys
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/** The caller's own passkeys and logins under `/api/v1/me` (D-0213, D-0217, D-0221). */
@ApiTest
class OwnSecurityTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired entities: PublicKeyCredentialUserEntityRepository,
    @Autowired credentials: UserCredentialRepository,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val passkeys = TestPasskeys(entities, credentials)
    private val authenticators = TestAuthenticators(oauth, testUsers)

    @Test
    fun `a user renames their passkey, and removing it needs the password`() {
        val user = testUsers.create()
        passkeys.register(user.id)
        val token = testUsers.token(user)
        val id = passkeyIds(token).single()

        val renamed = oauth.call("PUT", "$PASSKEYS/$id", token, mapOf("label" to "  Work laptop "))
        renamed.statusCode() shouldBe OK
        oauth.json(renamed)["label"] shouldBe "Work laptop"
        oauth.call("PUT", "$PASSKEYS/$id", token, mapOf("label" to " ")).statusCode() shouldBe BAD_REQUEST
        oauth.call("DELETE", "$PASSKEYS/$id", token, mapOf("password" to "wrong")).statusCode() shouldBe BAD_REQUEST
        oauth.call("DELETE", "$PASSKEYS/$id", token, password()).statusCode() shouldBe NO_CONTENT
        passkeyIds(token).shouldBeEmpty()
    }

    @Test
    fun `nobody reaches another user's passkey`() {
        val user = testUsers.create()
        passkeys.register(user.id)
        val id = passkeyIds(testUsers.token(user)).single()
        val other = testUsers.token(testUsers.create(BuiltInRole.OWNER.id))

        oauth.call("PUT", "$PASSKEYS/$id", other, mapOf("label" to "Mine now")).statusCode() shouldBe NOT_FOUND
        oauth.call("DELETE", "$PASSKEYS/$id", other, password()).statusCode() shouldBe NOT_FOUND
        passkeyIds(testUsers.token(user)) shouldBe listOf(id)
    }

    @Test
    fun `an owner keeps their last passkey unless an authenticator stands in`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)
        passkeys.register(owner.id)
        val token = testUsers.token(owner)
        val only = passkeyIds(token).single()

        oauth.call("DELETE", "$PASSKEYS/$only", token, password()).statusCode() shouldBe CONFLICT
        passkeys.register(owner.id)
        oauth.call("DELETE", "$PASSKEYS/$only", token, password()).statusCode() shouldBe NO_CONTENT
        passkeyIds(token) shouldHaveSize 1
    }

    @Test
    fun `a user sees which login is this one and signs another device out`() {
        val user = testUsers.create()
        val here = signIn(user)
        val there = signIn(user)

        val logins = logins(here.accessToken)
        logins.map { it["current"] } shouldContainExactlyInAnyOrder listOf(true, false)
        logins.map { it["clientId"] }.toSet() shouldBe setOf(FirstPartyClients.WEB)
        val other = logins.single { it["current"] == false }["id"]

        oauth.call("DELETE", "$LOGINS/$other", here.accessToken).statusCode() shouldBe NO_CONTENT
        oauth.refresh(FirstPartyClients.WEB, refreshOf(there)).statusCode() shouldBe BAD_REQUEST
        oauth.refresh(FirstPartyClients.WEB, refreshOf(here)).statusCode() shouldBe OK
        oauth.call("DELETE", "$LOGINS/$other", here.accessToken).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `a tab holding an older token still sees its own login as this one`() {
        val user = testUsers.create()
        val here = signIn(user)
        signIn(user)
        val refreshed = oauth.tokens(oauth.refresh(FirstPartyClients.WEB, refreshOf(here)))

        val current = currentLogins(here.accessToken)
        current shouldHaveSize 1
        currentLogins(refreshed.accessToken) shouldBe current
    }

    @Test
    fun `signing a device out also ends its browser's login session`() {
        val user = testUsers.create()
        val here = signIn(user)
        val verifier = verifier()
        val there =
            oauth.signInBrowser(
                FirstPartyClients.WEB,
                WEB_REDIRECT,
                user.username,
                TestUsers.PASSWORD,
                verifier,
            )
        oauth.exchange(FirstPartyClients.WEB, oauth.codeFrom(there.callback), WEB_REDIRECT, verifier)
        there.authorizeAgain(verifier()).path shouldBe URI(WEB_REDIRECT).path
        val other = logins(here.accessToken).single { it["current"] == false }["id"]

        oauth.call("DELETE", "$LOGINS/$other", here.accessToken).statusCode() shouldBe NO_CONTENT

        there.authorizeAgain(verifier()).path shouldBe "/login"
    }

    @Test
    fun `an owner with an authenticator removes their last passkey`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)
        passkeys.register(owner.id)
        authenticators.enroll(owner)
        val token = testUsers.token(owner)

        oauth.call("DELETE", "$PASSKEYS/${passkeyIds(token).single()}", token, password()).statusCode() shouldBe
            NO_CONTENT
        passkeyIds(token).shouldBeEmpty()
    }

    @Test
    fun `a login session cannot remove a passkey past the password and the factor rule`() {
        val user = testUsers.create()
        passkeys.register(user.id)
        val token = testUsers.token(user)
        val id = passkeyIds(token).single()
        val verifier = verifier()
        val browser =
            oauth.signInBrowser(
                FirstPartyClients.WEB,
                WEB_REDIRECT,
                user.username,
                TestUsers.PASSWORD,
                verifier,
            )

        browser.send("DELETE", "/webauthn/register/$id").statusCode() shouldBe FORBIDDEN
        passkeyIds(token) shouldBe listOf(id)
    }

    @Test
    fun `an owner removing two passkeys at once keeps one`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)
        repeat(2) { passkeys.register(owner.id) }
        val token = testUsers.token(owner)
        val ids = passkeyIds(token)
        val start = CountDownLatch(1)

        val statuses =
            Executors.newVirtualThreadPerTaskExecutor().use { threads ->
                ids
                    .map { id ->
                        threads.submit<Int> {
                            start.await()
                            oauth.call("DELETE", "$PASSKEYS/$id", token, password()).statusCode()
                        }
                    }.also { start.countDown() }
                    .map { it.get() }
            }

        statuses shouldContainExactlyInAnyOrder listOf(NO_CONTENT, CONFLICT)
        passkeyIds(token) shouldHaveSize 1
    }

    @Test
    fun `a passkey's name has at most 64 characters`() {
        val user = testUsers.create()
        passkeys.register(user.id)
        val token = testUsers.token(user)
        val id = passkeyIds(token).single()

        oauth.call("PUT", "$PASSKEYS/$id", token, mapOf("label" to "x".repeat(64))).statusCode() shouldBe OK
        oauth.call("PUT", "$PASSKEYS/$id", token, mapOf("label" to "x".repeat(65))).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `nobody signs out another user's device`() {
        val user = testUsers.create()
        val theirs = signIn(user)
        val id = logins(theirs.accessToken).single()["id"]

        val stranger = testUsers.token(testUsers.create(BuiltInRole.OWNER.id))
        oauth.call("DELETE", "$LOGINS/$id", stranger).statusCode() shouldBe NOT_FOUND
        oauth.refresh(FirstPartyClients.WEB, refreshOf(theirs)).statusCode() shouldBe OK
    }

    private fun passkeyIds(token: String) =
        oauth.jsonList(oauth.call("GET", PASSKEYS, token)).map { (it as Map<*, *>)["id"] as String }

    private fun logins(token: String) = oauth.jsonList(oauth.call("GET", LOGINS, token)).map { it as Map<*, *> }

    private fun currentLogins(token: String) = logins(token).filter { it["current"] == true }.map { it["id"] }

    private fun password() = mapOf("password" to TestUsers.PASSWORD)

    private fun verifier() = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"

    private fun signIn(user: WebUser): OAuthHttp.Tokens {
        val verifier = verifier()
        val callback = oauth.authorize(FirstPartyClients.WEB, WEB_REDIRECT, user.username, TestUsers.PASSWORD, verifier)
        return oauth.exchange(FirstPartyClients.WEB, oauth.codeFrom(callback), WEB_REDIRECT, verifier)
    }

    private fun refreshOf(tokens: OAuthHttp.Tokens) = tokens.refreshToken ?: error("No refresh token issued")

    private companion object {
        const val PASSKEYS = "/api/v1/me/passkeys"
        const val LOGINS = "/api/v1/me/logins"
        const val WEB_REDIRECT = "$TEST_ISSUER/auth/callback"
        const val OK = 200
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
    }
}
