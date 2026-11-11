package io.ogwars.cloud.api.auth.login

import io.ogwars.cloud.api.auth.AuthProperties
import io.ogwars.cloud.api.auth.mfa.MfaEndpoints
import io.ogwars.cloud.api.auth.mfa.MfaRequirements
import io.ogwars.cloud.domain.common.REDACTED
import io.ogwars.cloud.domain.user.MfaRequirement
import io.ogwars.cloud.service.SecretGenerator
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Configuration
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Where the login pages are in a sign-in, as the pages ask through [LoginStateController.state]. */
enum class LoginStep {
    /** Nobody is signed in: a password or a passkey. */
    CREDENTIALS,

    /** Signed in with a password; the authenticator's code follows (D-0097). */
    TOTP,

    /** Signed in with a password; this account's second factor is its passkey. */
    PASSKEY,

    /** An owner without a second factor sets one up before going on (D-0097). */
    ENROLMENT,

    /** Nothing is missing: the pages continue the app's authorization request. */
    DONE,
}

data class LoginCsrf(
    val headerName: String,
    val parameterName: String,
    val token: String,
) {
    override fun toString() = "LoginCsrf(headerName=$headerName, parameterName=$parameterName, token=$REDACTED)"
}

data class LoginState(
    val step: LoginStep,
    val csrf: LoginCsrf,
)

/**
 * Serves the branded login pages, built as a second entry of the web app (D-0212), each with a fresh CSP nonce
 * (D-0219); [LoginStateController] serves their state, and Spring's form, TOTP and WebAuthn endpoints take the sign-in.
 */
@Controller
class LoginPages(
    properties: AuthProperties,
    private val nonces: SecretGenerator,
) {
    // Configured pages that are missing fail the start, so a broken image never passes its probes.
    private val index: String? =
        properties.loginPages?.resolve(INDEX)?.let { file ->
            check(Files.isRegularFile(file)) { "ogcloud.auth.login-pages names no login pages: $file is missing" }
            Files.readString(file)
        }

    @GetMapping(LOGIN_PAGE, MfaEndpoints.PATH, ADD_PASSKEY_PAGE, produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun page(): ResponseEntity<String> {
        val page =
            index
                ?: return ResponseEntity
                    .status(
                        HttpStatus.SERVICE_UNAVAILABLE,
                    ).body("The login pages are not installed.")
        // A fresh nonce for every response, in the page and its CSP, so only the page's own styles load (D-0219).
        val nonce = nonces.next()
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .header(CONTENT_SECURITY_POLICY, policy(nonce))
            .body(page.replace(NONCE_PLACEHOLDER, nonce))
    }

    companion object {
        const val LOGIN_PAGE = "/login"

        /** Adds a passkey for a full login, then returns to the dashboard's account page (D-0218). */
        const val ADD_PASSKEY_PAGE = "/login/passkeys/new"
        const val STATE_PATH = "/login/state"

        /** Ends this browser's login session, with the pages' CSRF token; the dashboard's sign-out posts it (D-0227). */
        const val LOGOUT_PATH = "/login/logout"
        const val ASSETS = "/login/assets/**"
        const val CONTENT_SECURITY_POLICY = "Content-Security-Policy"
        private const val INDEX = "index.html"

        /** The placeholder in the built page's `<meta name="csp-nonce">`, replaced in every response. */
        private const val NONCE_PLACEHOLDER = "{{cspNonce}}"

        // No form-action: Chromium applies it to a form's redirects too, and a sign-in redirects to each app's own
        // address, such as the desktop app's loopback port or Grafana's host.
        private fun policy(nonce: String) =
            "default-src 'none'; script-src 'self'; style-src 'self' 'nonce-$nonce'; img-src 'self' data:; " +
                "font-src 'self'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'"
    }
}

/** What the login pages need to render (D-0212); part of the OpenAPI contract, so the pages' types are generated. */
@RestController
@RequestMapping("/login")
class LoginStateController(
    private val requirements: MfaRequirements,
) {
    @GetMapping("/state", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun state(request: HttpServletRequest): ResponseEntity<LoginState> {
        val csrf =
            request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
                ?: error("Spring Security's CSRF filter did not run for ${request.requestURI}")
        val login = SecurityContextHolder.getContext().authentication
        val step =
            if (login == null || !login.isAuthenticated || login is AnonymousAuthenticationToken) {
                LoginStep.CREDENTIALS
            } else {
                when (requirements.of(login)) {
                    MfaRequirement.NONE -> LoginStep.DONE
                    MfaRequirement.TOTP_CODE -> LoginStep.TOTP
                    MfaRequirement.PASSKEY_SIGN_IN -> LoginStep.PASSKEY
                    MfaRequirement.ENROLLMENT -> LoginStep.ENROLMENT
                }
            }
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(LoginState(step, LoginCsrf(csrf.headerName, csrf.parameterName, csrf.token)))
    }
}

/** The pages' hashed scripts, styles and fonts never change under one name, so browsers keep them a year. */
@Configuration(proxyBeanMethods = false)
class LoginAssets(
    private val properties: AuthProperties,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val directory = properties.loginPages ?: return
        registry
            .addResourceHandler(LoginPages.ASSETS)
            .addResourceLocations(directory.resolve(ASSETS_DIRECTORY).toUri().toString())
            .setCacheControl(CacheControl.maxAge(ASSET_LIFETIME_DAYS, TimeUnit.DAYS).cachePublic().immutable())
    }

    private companion object {
        const val ASSETS_DIRECTORY = "assets/"
        const val ASSET_LIFETIME_DAYS = 365L
    }
}
