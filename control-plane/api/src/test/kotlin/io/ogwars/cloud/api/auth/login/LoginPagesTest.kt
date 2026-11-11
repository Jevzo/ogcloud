package io.ogwars.cloud.api.auth.login

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.http.HttpResponse

/** The login pages carry a strict CSP whose style nonce is fresh in every response and matches the page (D-0219). */
@ApiTest
class LoginPagesTest(
    @LocalServerPort port: Int,
) {
    private val oauth = OAuthHttp(port)

    @Test
    fun `each page names a fresh nonce in its CSP and its meta tag`() {
        val first = oauth.get(LoginPages.LOGIN_PAGE)
        val second = oauth.get(LoginPages.LOGIN_PAGE)

        first.statusCode() shouldBe OK
        val nonce = nonceOf(first)
        first.body() shouldContain """<meta name="csp-nonce" content="$nonce" />"""
        first.body() shouldNotContain "{{cspNonce}}"
        nonceOf(second) shouldNotBe nonce
    }

    @Test
    fun `scripts load only from the API's own origin`() {
        val policy =
            oauth
                .get(
                    LoginPages.LOGIN_PAGE,
                ).headers()
                .firstValue(LoginPages.CONTENT_SECURITY_POLICY)
                .orElse("")

        policy shouldContain "script-src 'self';"
        policy shouldContain "default-src 'none'"
        policy shouldContain "frame-ancestors 'none'"
    }

    private fun nonceOf(response: HttpResponse<String>): String =
        NONCE.find(response.headers().firstValue(LoginPages.CONTENT_SECURITY_POLICY).orElse(""))?.groupValues?.get(1)
            ?: error("The CSP names no nonce")

    private companion object {
        const val OK = 200
        val NONCE = Regex("'nonce-([^']+)'")
    }
}
