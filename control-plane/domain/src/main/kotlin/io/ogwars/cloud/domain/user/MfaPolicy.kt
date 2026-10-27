package io.ogwars.cloud.domain.user

/** How a login proved itself so far. */
enum class Factor {
    PASSWORD,
    TOTP,
    PASSKEY,
}

/** What a login still needs before the apps may get tokens (D-0097). */
enum class MfaRequirement {
    NONE,

    /** Enter a code from the enrolled authenticator app. */
    TOTP_CODE,

    /** Sign in with the enrolled passkey, the only second factor this user has. */
    PASSKEY_SIGN_IN,

    /** An owner without any second factor enrolls one first. */
    ENROLLMENT,
}

/** Owners need a second factor; anyone who enrolled TOTP uses it; a passkey alone is enough (D-0097). */
object MfaPolicy {
    fun requirement(
        factors: Set<Factor>,
        isOwner: Boolean,
        hasTotp: Boolean,
        hasPasskey: Boolean,
    ): MfaRequirement =
        when {
            Factor.PASSKEY in factors -> MfaRequirement.NONE
            Factor.TOTP in factors -> MfaRequirement.NONE
            hasTotp -> MfaRequirement.TOTP_CODE
            !isOwner -> MfaRequirement.NONE
            hasPasskey -> MfaRequirement.PASSKEY_SIGN_IN
            else -> MfaRequirement.ENROLLMENT
        }
}
