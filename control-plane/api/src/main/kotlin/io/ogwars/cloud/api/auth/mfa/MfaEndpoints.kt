package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.domain.common.REDACTED
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.MfaRequirement
import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.FactorGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.time.Clock
import java.util.UUID

/**
 * The second step of a password login (D-0097): a TOTP code, or for an owner without a factor, setting one up. The
 * login pages (D-0212) render it; these endpoints take its forms and resume the app's authorization request.
 */
@Controller
@RequestMapping(MfaEndpoints.PATH)
class MfaEndpoints(
    private val requirements: MfaRequirements,
    private val totp: TotpService,
    private val users: WebUserStore,
    private val clock: Clock,
) {
    private val requestCache = HttpSessionRequestCache()
    private val contexts = HttpSessionSecurityContextRepository()

    @Hidden
    @PostMapping("/totp")
    fun verify(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam code: String,
    ) {
        if (totp.verify(userId(), code)) {
            completeSecondFactor(request, response)
        } else {
            response.sendRedirect("$PATH?error")
        }
    }

    /** An owner's pending authenticator secret; EnrollmentGate lets only logins that may enrol reach it. */
    @GetMapping("/totp-setup", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun setup(): ResponseEntity<TotpSetup> {
        val user = users.find(userId())?.value ?: throw NoSuchElementException("Unknown user")
        val enrollment = totp.pendingOrStart(user)
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(TotpSetup(enrollment.secret, enrollment.uri))
    }

    @Hidden
    @PostMapping("/totp-setup")
    fun confirmSetup(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam code: String,
    ) {
        try {
            totp.confirm(userId(), code)
        } catch (_: IllegalArgumentException) {
            response.sendRedirect("$PATH?error")
            return
        }
        completeSecondFactor(request, response)
    }

    /** Where the pages go once nothing is missing, such as after registering a passkey. */
    @Hidden
    @GetMapping("/continue")
    fun continueSignIn(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (requirements.of(login()) == MfaRequirement.NONE) {
            resume(request, response)
        } else {
            response.sendRedirect(PATH)
        }
    }

    // A new session id for the stronger login, then back to where the app's authorization request left off.
    private fun completeSecondFactor(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val login = login()
        val factor =
            FactorGrantedAuthority
                .withAuthority(MfaRequirements.TOTP_AUTHORITY)
                .issuedAt(clock.instant())
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

    companion object {
        const val PATH = "/login/mfa"
    }
}

/** An authenticator secret to add, as text and as an `otpauth://` URI for a QR code. */
data class TotpSetup(
    val secret: String,
    val uri: String,
) {
    override fun toString() = "TotpSetup(secret=$REDACTED, uri=$REDACTED)"
}
