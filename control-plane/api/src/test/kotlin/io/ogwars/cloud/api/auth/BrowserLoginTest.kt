package io.ogwars.cloud.api.auth

import io.ogwars.cloud.apitest.ApiTestSetup
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.Base32
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.Totp
import com.google.gson.JsonObject
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * The interim login pages in headless Chromium (D-0083, D-0099). Passkeys need the page's origin to be the relying
 * party, so this context serves the issuer itself on a fixed port, and a virtual authenticator stands in for a device.
 */
@ApiTestSetup
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowserLoginTest(
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
) {
    private val testUsers = TestUsers(users, passwords)
    private val playwright = Playwright.create()
    private val browser: Browser = playwright.chromium().launch()
    private val totp = Totp()
    private val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"

    @AfterAll
    fun close() {
        browser.close()
        playwright.close()
    }

    @Test
    fun `a wrong password stays on the login page`() {
        val user = testUsers.create()
        browser.newContext().use { context ->
            val page = context.newPage()
            page.navigate(authorizeUrl())
            signIn(page, user.username, "not the password")

            page.url() shouldContain "/login?error"
        }
    }

    @Test
    fun `an owner enrolls an authenticator, then a passkey, and signs in with each`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)
        browser.newContext().use { context ->
            val page = context.newPage()
            addVirtualAuthenticator(context, page)

            page.navigate(authorizeUrl())
            signIn(page, owner.username, TestUsers.PASSWORD)
            page.getByText("Use an authenticator app").click()
            val secret = Base32.decode(page.locator("code").textContent() ?: error("The setup page shows no secret"))
            page.locator("#code").fill(totp.code(secret, totp.step(Instant.now())))
            page.getByRole(AriaRole.BUTTON).click()
            callbackCode(page).shouldNotBeNull()

            page.navigate("$origin/webauthn/register")
            page.locator("#label").fill("Test passkey")
            page.locator("#register").click()
            page.waitForURL("**/webauthn/register?success")

            context.clearCookies()
            page.navigate(authorizeUrl())
            page.locator("#passkey-signin").click()
            callbackCode(page).shouldNotBeNull()
        }
    }

    private fun signIn(
        page: Page,
        username: String,
        password: String,
    ) {
        page.locator("#username").fill(username)
        page.locator("#password").fill(password)
        page.locator("form.login-form button[type=submit]").first().click()
        page.waitForLoadState()
    }

    private fun callbackCode(page: Page): String? {
        page.waitForURL("**/auth/callback?**")
        return OAuthHttp.query(URI(page.url()))["code"]
    }

    private fun addVirtualAuthenticator(
        context: BrowserContext,
        page: Page,
    ) {
        val cdp = context.newCDPSession(page)
        cdp.send("WebAuthn.enable")
        val options =
            JsonObject().apply {
                addProperty("protocol", "ctap2")
                addProperty("transport", "internal")
                addProperty("hasResidentKey", true)
                addProperty("hasUserVerification", true)
                addProperty("isUserVerified", true)
                addProperty("automaticPresenceSimulation", true)
            }
        cdp.send("WebAuthn.addVirtualAuthenticator", JsonObject().apply { add("options", options) })
    }

    private fun authorizeUrl(): String =
        "$origin/oauth2/authorize?response_type=code&client_id=${FirstPartyClients.WEB}&scope=openid&state=s" +
            "&redirect_uri=$origin/auth/callback&code_challenge=${OAuthHttp.challenge(
                verifier,
            )}&code_challenge_method=S256"

    companion object {
        private val port = ServerSocket(0).use { it.localPort }
        private val origin = "http://localhost:$port"

        @JvmStatic
        @DynamicPropertySource
        fun origin(registry: DynamicPropertyRegistry) {
            registry.add("server.port") { port }
            registry.add("ogcloud.auth.issuer") { origin }
        }
    }
}
