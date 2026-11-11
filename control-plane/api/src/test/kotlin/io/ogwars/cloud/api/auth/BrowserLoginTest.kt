package io.ogwars.cloud.api.auth

import io.ogwars.cloud.api.auth.mfa.Passkeys
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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
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
 * The login pages (D-0212, D-0099) in headless Chromium, as built by `mise run build:login`. Passkeys need the page's
 * origin to be the relying party, so this context serves the issuer itself on a fixed port, and a virtual
 * authenticator stands in for a device.
 */
@ApiTestSetup
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BrowserLoginTest(
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val passkeys: Passkeys,
) {
    private val testUsers = TestUsers(users, passwords)
    private val playwright = Playwright.create()
    private val browser: Browser = playwright.chromium().launch()
    private val totp = Totp()
    private val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"

    // What the pages' CSP refused (D-0219); any refusal fails the test that caused it.
    private val refused = mutableListOf<String>()

    @AfterEach
    fun nothingWasRefused() {
        val seen = refused.toList()
        refused.clear()
        seen.shouldBeEmpty()
    }

    @AfterAll
    fun close() {
        browser.close()
        playwright.close()
    }

    @Test
    fun `a wrong password stays on the login page and says so`() {
        val user = testUsers.create()
        browser.newContext().use { context ->
            val page = pageIn(context)
            page.navigate(authorizeUrl())
            signIn(page, user.username, "not the password")

            page.url() shouldContain "/login?error"
            page.getByText("That username and password do not match.").waitFor()
        }
    }

    @Test
    fun `an owner enrolls an authenticator app and continues to the app`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)
        browser.newContext().use { context ->
            val page = pageIn(context)
            page.navigate(authorizeUrl())
            signIn(page, owner.username, TestUsers.PASSWORD)

            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Authenticator app")).click()
            val secret =
                Base32.decode(
                    page.locator("span.font-mono").first().textContent() ?: error("The setup shows no secret"),
                )
            page.getByLabel("Code from the app").fill(totp.code(secret, totp.step(Instant.now())))
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Confirm")).click()

            callbackCode(page).shouldNotBeNull()
        }
    }

    @Test
    fun `an owner enrolls a passkey and then signs in with it alone`() {
        val owner = testUsers.create(BuiltInRole.OWNER.id)
        browser.newContext().use { context ->
            val page = pageIn(context)
            addVirtualAuthenticator(context, page)
            page.navigate(authorizeUrl())
            signIn(page, owner.username, TestUsers.PASSWORD)

            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Passkey")).click()
            page.getByLabel("Passkey name").fill("Test passkey")
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Register passkey")).click()
            callbackCode(page).shouldNotBeNull()

            context.clearCookies()
            page.navigate(authorizeUrl())
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign in with a passkey")).click()
            callbackCode(page).shouldNotBeNull()
        }
    }

    @Test
    fun `a user adds a passkey after signing in and goes back to their account`() {
        val user = testUsers.create()
        browser.newContext().use { context ->
            val page = pageIn(context)
            addVirtualAuthenticator(context, page)
            page.navigate("$origin/login/passkeys/new")
            signIn(page, user.username, TestUsers.PASSWORD)

            page.getByLabel("Passkey name").fill("Desk key")
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Register passkey")).click()
            page.waitForURL("**/account")
        }
        passkeys.list(user.id).map { it.label } shouldBe listOf("Desk key")
    }

    private fun pageIn(context: BrowserContext): Page =
        context.newPage().apply {
            onConsoleMessage { message ->
                if (message.text().contains("Content Security Policy")) refused += message.text()
            }
        }

    private fun signIn(
        page: Page,
        username: String,
        password: String,
    ) {
        page.getByLabel("Username or email").fill(username)
        page.getByLabel("Password").fill(password)
        page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign in").setExact(true)).click()
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
            // A defined port keeps actuator's fixed 8081 too (D-0190), which other test JVMs may hold.
            registry.add("management.server.port") { 0 }
            registry.add("ogcloud.auth.issuer") { origin }
        }
    }
}
