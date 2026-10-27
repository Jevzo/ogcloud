package io.ogwars.cloud.apitest

import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.Base64

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
    ): URI {
        val browser = Browser()
        val next = browser.signIn(clientId, redirectUri, login, password, verifier)
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
        val page = browser.get("$MFA_PAGE/totp-setup").body()
        val secret = SECRET.find(page)?.groupValues?.get(1) ?: error("The setup page shows no secret")
        val csrf = CSRF.find(page)?.groupValues?.get(1) ?: error("The setup page has no CSRF token")
        val afterCode =
            browser
                .post(
                    "$MFA_PAGE/totp-setup",
                    form(listOf("code" to codeFor(secret), "_csrf" to csrf)),
                ).location()
        return browser.get(afterCode.toString()).location()
    }

    /** The passkey sign-in options the login page's script requests. */
    fun passkeyOptions(): HttpResponse<String> {
        val browser = Browser()
        val csrf =
            CSRF.find(browser.get("/login").body())?.groupValues?.get(1) ?: error("The login page has no CSRF token")
        return browser.postJson("/webauthn/authenticate/options", csrf)
    }

    fun codeFrom(callback: URI): String = query(callback)["code"] ?: error("No code in $callback")

    fun exchange(
        clientId: String,
        code: String,
        redirectUri: String,
        verifier: String,
    ): Tokens =
        tokens(
            token(
                "grant_type" to "authorization_code",
                "client_id" to clientId,
                "code" to code,
                "redirect_uri" to redirectUri,
                "code_verifier" to verifier,
            ),
        )

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
        val basic = Base64.getEncoder().encodeToString("$clientId:$secret".toByteArray())
        val body = form(listOf("grant_type" to "client_credentials", "scope" to scopes.joinToString(" ")))
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
    private inner class Browser {
        private val cookies = mutableMapOf<String, String>()

        fun signIn(
            clientId: String,
            redirectUri: String,
            login: String,
            password: String,
            verifier: String?,
        ): URI {
            val parameters =
                listOfNotNull(
                    "response_type" to "code",
                    "client_id" to clientId,
                    "redirect_uri" to redirectUri,
                    "scope" to "openid",
                    "state" to STATE,
                    verifier?.let { "code_challenge" to challenge(it) },
                    verifier?.let { "code_challenge_method" to "S256" },
                )
            val toLogin = get("/oauth2/authorize?${form(parameters)}")
            if (toLogin.statusCode() != FOUND || !toLogin.location().path.endsWith("/login")) return toLogin.location()
            val csrf = CSRF.find(get("/login").body())?.groupValues?.get(1) ?: error("The login page has no CSRF token")
            val afterLogin = post("/login", form(listOf("username" to login, "password" to password, "_csrf" to csrf)))
            return get(afterLogin.location().toString()).location()
        }

        fun get(path: String) = exchange(HttpRequest.newBuilder(uri(path)).header("Accept", "text/html").GET())

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

        fun secondFactor(code: String): HttpResponse<String> {
            val csrf = CSRF.find(get(MFA_PAGE).body())?.groupValues?.get(1) ?: error("The MFA page has no CSRF token")
            return post("$MFA_PAGE/totp", form(listOf("code" to code, "_csrf" to csrf)))
        }

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
        private const val OK = 200
        private const val FOUND = 302
        private const val FORM = "application/x-www-form-urlencoded"
        private val CSRF = Regex("""name="_csrf"[^>]*value="([^"]+)"""")
        private val SECRET = Regex("""<code>([A-Z2-7]+)</code>""")

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
