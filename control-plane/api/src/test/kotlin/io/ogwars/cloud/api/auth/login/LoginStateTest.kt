package io.ogwars.cloud.api.auth.login

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestAuthenticators
import io.ogwars.cloud.apitest.TestPasskeys
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository

/** `/login/state` tells the login pages which step to show (D-0212), from what the login still owes (D-0097). */
@ApiTest
class LoginStateTest(
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
    fun `nobody signed in gives credentials`() {
        oauth.loginStep() shouldBe LoginStep.CREDENTIALS.name
    }

    @Test
    fun `a password without a second factor owed is done`() {
        oauth.loginStep(password(testUsers.create())) shouldBe LoginStep.DONE.name
    }

    @Test
    fun `an authenticator asks for its code, and an owner's only passkey for itself`() {
        val withApp = testUsers.create().also(authenticators::enroll)
        val withPasskey = testUsers.create(BuiltInRole.OWNER.id).also { passkeys.register(it.id) }

        oauth.loginStep(password(withApp)) shouldBe LoginStep.TOTP.name
        oauth.loginStep(password(withPasskey)) shouldBe LoginStep.PASSKEY.name
    }

    @Test
    fun `an owner without a second factor enrols one`() {
        oauth.loginStep(password(testUsers.create(BuiltInRole.OWNER.id))) shouldBe LoginStep.ENROLMENT.name
    }

    private fun password(user: WebUser) = user.username to TestUsers.PASSWORD
}
