package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.Factor
import io.ogwars.cloud.domain.user.MfaPolicy
import io.ogwars.cloud.domain.user.MfaRequirement
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.FactorGrantedAuthority
import org.springframework.stereotype.Component
import java.util.UUID

/** Applies [MfaPolicy] to a login session (D-0097). */
@Component
class MfaRequirements(
    private val users: WebUserStore,
    private val totp: TotpService,
    private val passkeys: Passkeys,
) {
    fun of(login: Authentication): MfaRequirement {
        // Unknown or disabled users get no tokens anyway (TokenClaims); nothing to enforce here.
        val id = runCatching { UUID.fromString(login.name) }.getOrNull() ?: return MfaRequirement.NONE
        val user = users.find(id)?.value ?: return MfaRequirement.NONE
        val factors = login.authorities.mapNotNull { FACTORS[it.authority] }.toSet()
        return MfaPolicy.requirement(factors, user.isOwner, totp.isEnrolled(id), passkeys.exist(id))
    }

    companion object {
        /** Our own factor authority; Spring defines the others (`FACTOR_PASSWORD`, `FACTOR_WEBAUTHN`). */
        val TOTP_AUTHORITY: String = FactorGrantedAuthority.withFactor("TOTP").build().authority

        private val FACTORS =
            mapOf(
                FactorGrantedAuthority.PASSWORD_AUTHORITY to Factor.PASSWORD,
                FactorGrantedAuthority.WEBAUTHN_AUTHORITY to Factor.PASSKEY,
                TOTP_AUTHORITY to Factor.TOTP,
            )
    }
}
