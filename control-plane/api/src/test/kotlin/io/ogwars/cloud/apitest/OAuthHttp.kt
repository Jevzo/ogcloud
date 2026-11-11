package io.ogwars.cloud.apitest

import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.Base64
import java.util.stream.Stream

/** Drives the API over HTTP the way the apps will: a browser login, then token requests (D-0012, D-0090). */
class OAuthHttp(
    private val port: Int,
) {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
    private val json = JsonMapper.builder().build()

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
    )

    /**
     * Signs in through the login form and returns where the server sent the browser: the app's redirect with a
     * code, or the MFA page. With [totpCode], a requested code is entered on the way.
     */
    fun authorize(
        clientId: String,
        redirectUri: String,
        login: String,
        password: String,
        verifier: String?,
        totpCode: (() -> String)? = null,
        scope: String = "openid",
    ): URI {
        val browser = Browser()
        val next = browser.signIn(clientId, redirectUri, login, password, verifier, scope)
        if (totpCode == null || next.path != MFA_PAGE) return next
        val afterCode = browser.secondFactor(totpCode()).location()
        return if (afterCode.path == MFA_PAGE) afterCode else browser.get(afterCode.toString()).location()
    }

    /** As [authorize] for an owner without a second factor: enrolls an authenticator on the way; [codeFor] gets its secret. */
    fun enrollDuringLogin(
        clientId: String,
        redirectUri: String,
        login: String,
        password: String,
        verifier: String,
        codeFor: (String) -> String,
    ): URI {
        val browser = Browser()
        check(browser.signIn(clientId, redirectUri, login, password, verifier).path == MFA_PAGE) {
            "No second factor was asked for"
        }
        val setup = browser.getJson("$MFA_PAGE/totp-setup").body()
        val secret = SECRET.find(setup)?.groupValues?.get(1) ?: error("The setup answer holds no secret")
        val csrf = browser.csrf()
        val afterCode =
            browser
                .post(
                    "$MFA_PAGE/totp-setup",
                    form(listOf("code" to codeFor(secret), "_csrf" to csrf)),
                ).location()
        return browser.get(afterCode.toString()).location()
    }

    /**
     * Signs in with the password alone and, while a second factor is still due, tries to add one: the statuses of
     * the TOTP setup page, its form, and passkey registration. The last two show that the CSRF token was accepted.
     */
    fun enrollWithPasswordOnly(
        clientId: String,
        redirectUri: String,
        login: String,
        password: String,
        verifier: String,
    ): List<Int> {
        val browser = Browser()
        check(browser.signIn(clientId, redirectUri, login, password, verifier).path == MFA_PAGE) {
            "No second factor was asked for"
        }
        val csrf = browser.csrf()
        val code = form(listOf("code" to "000000", "_csrf" to csrf))
        return listOf(
            browser.getJson("$MFA_PAGE/totp-setup").statusCode(),
            browser.post("$MFA_PAGE/totp-setup", code).statusCode(),
            browser.postJson("/webauthn/register/options", csrf).statusCode(),
            browser.post("$MFA_PAGE/totp", code).statusCode(),
            browser.postJson("/webauthn/authenticate/options", csrf).statusCode(),
        )
    }

    /**
     * Opens a login-chain page in a fresh browser, which sends it to sign in with the password first, and returns the
     * path it ends on: the page itself, or the MFA page while a second factor is due.
     */
    fun openAfterSignIn(
        path: String,
        login: String,
        password: String,
    ): String {
        val browser = Browser()
        val toLogin = browser.get(path)
        check(
            toLogin.statusCode() == FOUND && toLogin.location().path == "/login",
        ) { "$path did not ask for a sign-in" }
        val target =
            browser
                .post("/login", form(listOf("username" to login, "password" to password, "_csrf" to browser.csrf())))
                .location()
        val landed = browser.get(target.toString())
        return if (landed.statusCode() == FOUND) landed.location().path else target.path
    }

    /** As [authorize] without a second factor, but the browser stays: its cookies still hold the login session. */
    fun signInBrowser(
        clientId: String,
        redirectUri: String,
        login: String,
        password: String,
        verifier: String,
    ): SignedInBrowser {
        val browser = Browser()
        val callback = browser.signIn(clientId, redirectUri, login, password, verifier)
        return SignedInBrowser(browser, clientId, redirectUri, callback)
    }

    /** A browser after a sign-in that ended at [callback]. */
    inner class SignedInBrowser internal constructor(
        private val browser: Browser,
        private val clientId: String,
        private val redirectUri: String,
        val callback: URI,
    ) {
        /** The login session's id, from the cookie Spring Session set (base64 of the id). */
        val sessionId: String get() = browser.sessionId()

        /** Where `/oauth2/authorize` sends this browser now: back to the app while its login session lives. */
        fun authorizeAgain(verifier: String): URI =
            browser.startAuthorize(clientId, redirectUri, verifier, "openid").location()

        /** An API call from this browser: the dashboard's calls carry the session cookie next to the token. */
        fun call(
            path: String,
            accessToken: String,
        ): HttpResponse<String> = browser.getWithToken(path, accessToken)
    }

    /** The step `/login/state` names for a fresh browser, or for one that signed in with [login]'s password. */
    fun loginStep(login: Pair<String, String>? = null): String {
        val browser = Browser()
        if (login != null) {
            val (username, password) = login
            browser.post(
                "/login",
                form(listOf("username" to username, "password" to password, "_csrf" to browser.csrf())),
            )
        }
        return STEP.find(browser.getJson("/login/state").body())?.groupValues?.get(1)
            ?: error("The login state names no step")
    }

    /** The passkey sign-in options the login page's script requests. */
    fun passkeyOptions(): HttpResponse<String> {
        val browser = Browser()
        return browser.postJson("/webauthn/authenticate/options", browser.csrf())
    }

    fun codeFrom(callback: URI): String = query(callback)["code"] ?: error("No code in $callback")

    /** Redeems a code; a confidential client also sends its [secret] (client_secret_basic). */
    fun exchange(
        clientId: String,
        code: String,
        redirectUri: String,
        verifier: String,
        secret: String? = null,
    ): Tokens = tokens(exchangeResponse(clientId, code, redirectUri, verifier, secret))

    fun exchangeResponse(
        clientId: String,
        code: String,
        redirectUri: String,
        verifier: String,
        secret: String? = null,
    ): HttpResponse<String> {
        val parameters =
            listOf(
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
            )
        return if (secret == null) token(*parameters.toTypedArray()) else postBasic(clientId, secret, form(parameters))
    }

    fun refresh(
        clientId: String,
        refreshToken: String,
    ): HttpResponse<String> =
        token(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to refreshToken,
        )

    fun revoke(
        clientId: String,
        token: String,
    ): HttpResponse<String> = post("/oauth2/revoke", form(listOf("client_id" to clientId, "token" to token)))

    fun clientCredentials(
        clientId: String,
        secret: String,
        scopes: List<String>,
    ): HttpResponse<String> {
        val body = form(listOf("grant_type" to "client_credentials", "scope" to scopes.joinToString(" ")))
        return postBasic(clientId, secret, body)
    }

    private fun postBasic(
        clientId: String,
        secret: String,
        body: String,
    ): HttpResponse<String> {
        val basic = Base64.getEncoder().encodeToString("$clientId:$secret".toByteArray())
        return send(
            HttpRequest
                .newBuilder(uri("/oauth2/token"))
                .header("Authorization", "Basic $basic")
                .header("Content-Type", FORM)
                .POST(HttpRequest.BodyPublishers.ofString(body)),
        )
    }

    fun tokens(response: HttpResponse<String>): Tokens {
        check(response.statusCode() == OK) { "Token request failed: ${response.statusCode()} ${response.body()}" }
        val values = json.readValue(response.body(), Map::class.java)
        return Tokens(
            values["access_token"] as String,
            values["refresh_token"] as String?,
            values["id_token"] as String?,
        )
    }

    fun error(response: HttpResponse<String>): String? =
        json.readValue(response.body(), Map::class.java)["error"] as String?

    fun get(path: String): HttpResponse<String> = send(HttpRequest.newBuilder(uri(path)).GET())

    fun getWithToken(
        path: String,
        accessToken: String,
    ): HttpResponse<String> =
        send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer $accessToken").GET())

    fun postWithToken(
        path: String,
        accessToken: String,
    ): HttpResponse<String> =
        send(
            HttpRequest
                .newBuilder(
                    uri(path),
                ).header("Authorization", "Bearer $accessToken")
                .POST(HttpRequest.BodyPublishers.noBody()),
        )

    /** A JSON API call with a bearer token; [token] null calls anonymously. */
    fun call(
        method: String,
        path: String,
        token: String?,
        body: Any? = null,
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
        if (token != null) request.header("Authorization", "Bearer $token")
        val payload =
            body?.let { HttpRequest.BodyPublishers.ofString(json.writeValueAsString(it)) }
                ?: HttpRequest.BodyPublishers.noBody()
        return send(request.method(method, payload))
    }

    /** Opens the live-update stream; the body yields its lines as they arrive. */
    fun events(ticket: String): HttpResponse<Stream<String>> =
        http.send(
            HttpRequest
                .newBuilder(
                    uri("/api/v1/events?ticket=$ticket"),
                ).header("Accept", "text/event-stream")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofLines(),
        )

    /** With [chunked], the body is streamed without a Content-Length, as a client that does not know it would. */
    fun upload(
        path: String,
        token: String,
        body: ByteArray,
        contentType: String,
        chunked: Boolean = false,
    ): HttpResponse<String> {
        val publisher =
            if (chunked) {
                HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(body) }
            } else {
                HttpRequest.BodyPublishers.ofByteArray(body)
            }
        return send(
            HttpRequest
                .newBuilder(uri(path))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", contentType)
                .POST(publisher),
        )
    }

    fun download(
        path: String,
        token: String,
    ): HttpResponse<ByteArray> =
        http.send(
            HttpRequest
                .newBuilder(uri(path))
                .header("Authorization", "Bearer $token")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    fun json(response: HttpResponse<String>): Map<*, *> = json.readValue(response.body(), Map::class.java)

    fun jsonList(response: HttpResponse<String>): List<*> = json.readValue(response.body(), List::class.java)

    private fun token(vararg parameters: Pair<String, String>) = post("/oauth2/token", form(parameters.toList()))

    private fun post(
        path: String,
        body: String,
    ) = send(
        HttpRequest
            .newBuilder(
                uri(path),
            ).header("Content-Type", FORM)
            .POST(HttpRequest.BodyPublishers.ofString(body)),
    )

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        http.send(request.build(), HttpResponse.BodyHandlers.ofString())

    private fun uri(path: String): URI = if (path.startsWith("http")) URI(path) else URI("http://localhost:$port$path")

    /** Keeps the session cookie across requests, as a browser would. */
    internal inner class Browser {
        private val cookies = mutableMapOf<String, String>()

        fun signIn(
            clientId: String,
            redirectUri: String,
            login: String,
            password: String,
            verifier: String?,
            scope: String = "openid",
        ): URI {
            val toLogin = startAuthorize(clientId, redirectUri, verifier, scope)
            if (toLogin.statusCode() != FOUND || !toLogin.location().path.endsWith("/login")) return toLogin.location()
            val afterLogin =
                post("/login", form(listOf("username" to login, "password" to password, "_csrf" to csrf())))
            return get(afterLogin.location().toString()).location()
        }

        fun startAuthorize(
            clientId: String,
            redirectUri: String,
            verifier: String?,
            scope: String,
        ): HttpResponse<String> {
            val parameters =
                listOfNotNull(
                    "response_type" to "code",
                    "client_id" to clientId,
                    "redirect_uri" to redirectUri,
                    "scope" to scope,
                    "state" to STATE,
                    verifier?.let { "code_challenge" to challenge(it) },
                    verifier?.let { "code_challenge_method" to "S256" },
                )
            return get("/oauth2/authorize?${form(parameters)}")
        }

        fun get(path: String) = exchange(HttpRequest.newBuilder(uri(path)).header("Accept", "text/html").GET())

        fun getWithToken(
            path: String,
            accessToken: String,
        ) = exchange(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer $accessToken").GET())

        fun sessionId(): String =
            cookies[SESSION_COOKIE]?.let { String(Base64.getDecoder().decode(it)) } ?: error("No login session cookie")

        fun getJson(path: String) =
            exchange(HttpRequest.newBuilder(uri(path)).header("Accept", "application/json").GET())

        /** The login pages read their CSRF token from `/login/state` (D-0212). */
        fun csrf(): String =
            TOKEN.find(getJson("/login/state").body())?.groupValues?.get(1)
                ?: error("The login state has no CSRF token")

        fun post(
            path: String,
            body: String,
        ) = exchange(
            HttpRequest
                .newBuilder(uri(path))
                .header("Accept", "text/html")
                .header("Content-Type", FORM)
                .POST(HttpRequest.BodyPublishers.ofString(body)),
        )

        fun secondFactor(code: String): HttpResponse<String> =
            post("$MFA_PAGE/totp", form(listOf("code" to code, "_csrf" to csrf())))

        fun postJson(
            path: String,
            csrf: String,
        ) = exchange(
            HttpRequest
                .newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", csrf)
                .POST(HttpRequest.BodyPublishers.noBody()),
        )

        private fun exchange(request: HttpRequest.Builder): HttpResponse<String> {
            if (cookies.isNotEmpty()) {
                request.header(
                    "Cookie",
                    cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
                )
            }
            return send(request).also { response ->
                response.headers().allValues("Set-Cookie").forEach { cookie ->
                    val (name, value) = cookie.substringBefore(';').split('=', limit = 2)
                    cookies[name] = value
                }
            }
        }
    }

    companion object {
        const val STATE = "test-state"
        const val MFA_PAGE = "/login/mfa"
        private const val SESSION_COOKIE = "SESSION"
        private const val OK = 200
        private const val FOUND = 302
        private const val FORM = "application/x-www-form-urlencoded"
        private val TOKEN = Regex(""""token":"([^"]+)"""")
        private val STEP = Regex(""""step":"([A-Z]+)"""")
        private val SECRET = Regex(""""secret":"([A-Z2-7]+)"""")

        fun challenge(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            )

        fun query(uri: URI): Map<String, String> =
            uri.rawQuery
                .orEmpty()
                .split('&')
                .filter(String::isNotEmpty)
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }

        private fun form(parameters: List<Pair<String, String>>): String =
            parameters.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, Charsets.UTF_8)}" }

        private fun HttpResponse<String>.location(): URI =
            URI(
                headers()
                    .firstValue(
                        "Location",
                    ).orElseThrow { IllegalStateException("No redirect (${statusCode()}): ${body()}") },
            )
    }
}
