package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.MfaRequirement
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.FactorGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.util.HtmlUtils
import java.time.Clock
import java.util.Locale
import java.util.UUID

/**
 * The second step of a password login, until Phase 8 brands it (D-0083, D-0097): a TOTP code, a passkey sign-in,
 * or, for an owner without either, enrolling one. Strings come from `messages.properties` (D-0050).
 */
@Controller
@RequestMapping(MfaPage.PATH)
class MfaPage(
    private val requirements: MfaRequirements,
    private val totp: TotpService,
    private val users: WebUserStore,
    private val messages: MessageSource,
    private val clock: Clock,
) {
    private val requestCache = HttpSessionRequestCache()
    private val contexts = HttpSessionSecurityContextRepository()

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun show(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam error: String?,
    ): String? {
        val login = login()
        val body =
            when (requirements.of(login)) {
                MfaRequirement.NONE -> {
                    resume(request, response)
                    return null
                }

                MfaRequirement.TOTP_CODE -> {
                    codeForm(request, "$PATH/totp", "mfa.totp.prompt")
                }

                MfaRequirement.PASSKEY_SIGN_IN -> {
                    paragraph("mfa.passkey.prompt") + link("/login", "mfa.passkey.action")
                }

                MfaRequirement.ENROLLMENT -> {
                    paragraph("mfa.enroll.prompt") + link("$PATH/totp-setup", "mfa.enroll.totp") +
                        link("/webauthn/register", "mfa.enroll.passkey")
                }
            }
        return page(if (error != null) paragraph("mfa.error") + body else body)
    }

    @PostMapping("/totp")
    fun verify(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam code: String,
    ) {
        if (totp.verify(
                userId(),
                code,
            )
        ) {
            completeSecondFactor(request, response)
        } else {
            response.sendRedirect("$PATH?error")
        }
    }

    @GetMapping("/totp-setup", produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun setup(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam error: String?,
    ): String? {
        val user = users.find(userId())?.value ?: throw NoSuchElementException("Unknown user")
        if (totp.isEnrolled(user.id)) {
            // Confirmed meanwhile, e.g. in another tab: nothing left to set up.
            response.sendRedirect(PATH)
            return null
        }
        val enrollment = totp.pendingOrStart(user)
        val notice = if (error != null) paragraph("mfa.error") else ""
        return page(
            notice + paragraph("mfa.setup.prompt") +
                "<p><code>${escape(enrollment.secret)}</code></p>" +
                "<p><a href=\"${escape(enrollment.uri)}\">${text("mfa.setup.link")}</a></p>" +
                codeForm(request, "$PATH/totp-setup", "mfa.setup.confirm"),
        )
    }

    @PostMapping("/totp-setup")
    fun confirmSetup(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam code: String,
    ) {
        try {
            totp.confirm(userId(), code)
        } catch (_: IllegalArgumentException) {
            response.sendRedirect("$PATH/totp-setup?error")
            return
        }
        completeSecondFactor(request, response)
    }

    // A new session id for the stronger login, then back to where the app's authorization request left off.
    private fun completeSecondFactor(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val login = login()
        val factor =
            FactorGrantedAuthority
                .withAuthority(
                    MfaRequirements.TOTP_AUTHORITY,
                ).issuedAt(clock.instant())
                .build()
        val stronger =
            UsernamePasswordAuthenticationToken.authenticated(
                login.principal ?: error("A login always has a principal"),
                null,
                login.authorities + factor,
            )
        stronger.details = login.details
        val context = SecurityContextHolder.createEmptyContext().apply { authentication = stronger }
        SecurityContextHolder.setContext(context)
        request.changeSessionId()
        contexts.saveContext(context, request, response)
        resume(request, response)
    }

    private fun resume(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val saved = requestCache.getRequest(request, response)
        requestCache.removeRequest(request, response)
        response.sendRedirect(saved?.redirectUrl ?: "/")
    }

    private fun login(): Authentication =
        SecurityContextHolder.getContext().authentication ?: error("The login chain requires a login")

    private fun userId(): UUID = UUID.fromString(login().name)

    private fun codeForm(
        request: HttpServletRequest,
        action: String,
        prompt: String,
    ): String {
        val csrf = request.getAttribute(CsrfToken::class.java.name) as CsrfToken
        return """
            <form method="post" action="$action">
              <label for="code">${text(prompt)}</label>
              <input id="code" name="code" inputmode="numeric" autocomplete="one-time-code" autofocus required>
              <input type="hidden" name="${escape(csrf.parameterName)}" value="${escape(csrf.token)}">
              <button type="submit">${text("mfa.submit")}</button>
            </form>
            """.trimIndent()
    }

    private fun paragraph(key: String) = "<p>${text(key)}</p>"

    private fun link(
        href: String,
        key: String,
    ) = "<p><a href=\"$href\">${text(key)}</a></p>"

    private fun page(body: String) =
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>${text("mfa.title")}</title></head>" +
            "<body><main><h1>${text("mfa.title")}</h1>$body</main></body></html>"

    private fun text(key: String) = escape(messages.getMessage(key, null, Locale.ENGLISH))

    private fun escape(value: String): String = HtmlUtils.htmlEscape(value)

    companion object {
        const val PATH = "/login/mfa"
    }
}
