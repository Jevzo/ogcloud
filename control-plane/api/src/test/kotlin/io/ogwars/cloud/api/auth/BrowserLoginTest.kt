package io.ogwars.cloud.api.auth

import io.ogwars.cloud.api.auth.login.LoginPages
import io.ogwars.cloud.api.auth.mfa.Passkeys
import io.ogwars.cloud.apitest.ApiTestSetup
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.Base32
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.Totp
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.RequestOptions
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.ServerSocket
import java.net.URI
import java.time.Instant
import java.util.UUID

/**
 * The login pages (D-0212, D-0099) in headless Chromium, as built by `mise run build:login`. Passkeys need the page's
 * origin to be the relying party, so this context serves the issuer itself on a free port picked before it starts,
 * and a virtual authenticator stands in for a device.
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
    private val oauth = OAuthHttp(port)

    // What the pages' CSP refused (D-0219); any refusal fails the test that caused it.
    private val refused = mutableListOf<String>()

    // The app callbacks the browser was sent to, oldest first; the web app that would serve them is not running here.
    private val callbacks = ArrayDeque<String>()

    @AfterEach
    fun nothingWasRefused() {
        callbacks.clear()
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

            redeem(callbackCode(page))
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
            redeem(callbackCode(page))

            context.clearCookies()
            page.navigate(authorizeUrl())
            page.getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName("Sign in with a passkey")).click()
            redeem(callbackCode(page))
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
            page.waitForURL("**$ACCOUNT_PATH")
        }
        passkeys.list(user.id).map { it.label } shouldBe listOf("Desk key")
    }

    @Test
    fun `signing out through the OIDC logout ends the login session`() {
        signOutThenOpenAddPasskey { page, idToken ->
            page.navigate("$origin/oauth2/logout?post_logout_redirect_uri=$origin&id_token_hint=$idToken")
        }
    }

    @Test
    fun `the dashboard's own sign-out ends the login session without an id token`() {
        signOutThenOpenAddPasskey {
            page,
            _,
            ->
            postLogout(page.context(), withCsrf = true, HttpStatus.NO_CONTENT)
        }
    }

    @Test
    fun `a sign-out without the login pages' CSRF token is refused`() {
        val user = testUsers.create()
        browser.newContext().use { context ->
            signInAndRedeem(context, user.username)

            postLogout(context, withCsrf = false, HttpStatus.FORBIDDEN)
            val page = pageIn(context)
            page.navigate("$origin${LoginPages.ADD_PASSKEY_PAGE}")
            page.getByLabel("Passkey name").waitFor()
        }
    }

    /** Signs in, signs out with [signOut], then expects the add-passkey page to ask for a sign-in again. */
    private fun signOutThenOpenAddPasskey(signOut: (Page, String) -> Unit) {
        val user = testUsers.create()
        browser.newContext().use { context ->
            val idToken = signInAndRedeem(context, user.username)
            val page = pageIn(context)
            signOut(page, idToken)
            page.navigate("$origin${LoginPages.ADD_PASSKEY_PAGE}")

            page.getByLabel("Username or email").waitFor()
            URI(page.url()).path shouldNotBe LoginPages.ADD_PASSKEY_PAGE
        }
    }

    /**
     * Signs in with the password on a page of its own, redeems the code and returns the ID token. Here the API answers
     * the app's callback by sending the page back to sign in, whose state request makes the login's first CSRF token
     * since the sign-in and whose finished step continues to the app again, over and over. The page is closed once
     * that request is answered, so neither its token nor its navigations race the test's own requests.
     */
    private fun signInAndRedeem(
        context: BrowserContext,
        username: String,
    ): String {
        val page = pageIn(context)
        page.navigate(authorizeUrl())
        page.waitForResponse({ callbacks.isNotEmpty() && URI(it.url()).path == LoginPages.STATE_PATH }) {
            signIn(page, username, TestUsers.PASSWORD)
        }
        val code = callbackCode(page).shouldNotBeNull()
        page.close()
        return oauth
            .exchange(FirstPartyClients.WEB, code, "$origin$CALLBACK_PATH", verifier)
            .idToken
            .shouldNotBeNull()
    }

    /** Posts the login-chain logout with this browser's cookies, as the dashboard does (D-0227), and checks its answer. */
    private fun postLogout(
        context: BrowserContext,
        withCsrf: Boolean,
        expected: HttpStatus,
    ) {
        val state = JsonParser.parseString(context.request().get("$origin${LoginPages.STATE_PATH}").text()).asJsonObject
        val csrf = state.getAsJsonObject("csrf")
        val options = RequestOptions.create()
        if (withCsrf) options.setHeader(csrf.get("headerName").asString, csrf.get("token").asString)
        val response = context.request().post("$origin${LoginPages.LOGOUT_PATH}", options)
        withClue("The logout answered ${response.status()} (${response.text()}) to a login at ${state.get("step")}") {
            response.status() shouldBe expected.value()
        }
    }

    private fun pageIn(context: BrowserContext): Page =
        context.newPage().apply {
            onRequest { request -> if (URI(request.url()).path == CALLBACK_PATH) callbacks += request.url() }
            // The web app serves it in a cluster (D-0098); here the API alone would send the page to sign in.
            route({ URI(it).path == ACCOUNT_PATH }) { it.fulfill(Route.FulfillOptions().setBody("account")) }
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

    /** The code of the next app callback; the API answers the callback itself with a redirect to sign in. */
    private fun callbackCode(page: Page): String? {
        page.waitForCondition { callbacks.isNotEmpty() }
        return OAuthHttp.query(URI(callbacks.removeFirst()))["code"]
    }

    /** Redeems a callback's code as the web app would, which reads back everything the sign-in stored. */
    private fun redeem(code: String?) =
        oauth.exchange(FirstPartyClients.WEB, code.shouldNotBeNull(), "$origin$CALLBACK_PATH", verifier).accessToken

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
            "&redirect_uri=$origin$CALLBACK_PATH&code_challenge=${OAuthHttp.challenge(
                verifier,
            )}&code_challenge_method=S256"

    companion object {
        private const val CALLBACK_PATH = "/auth/callback"
        private const val ACCOUNT_PATH = "/account"
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
