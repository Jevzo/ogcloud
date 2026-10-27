package io.ogwars.cloud.api.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** bcrypt for every stored password and client secret (D-0089); nothing else is accepted. */
object Passwords {
    private const val BCRYPT = "bcrypt"
    private const val DISABLED = "disabled"

    /** The stored hash of an account without a password; it never matches. */
    const val NO_PASSWORD = "{$DISABLED}"

    fun encoder(): PasswordEncoder =
        DelegatingPasswordEncoder(BCRYPT, mapOf(BCRYPT to BCryptPasswordEncoder(), DISABLED to NeverMatches))

    private object NeverMatches : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?): String =
            throw UnsupportedOperationException("Nothing is encoded as disabled")

        override fun matches(
            rawPassword: CharSequence?,
            encodedPassword: String?,
        ): Boolean = false
    }
}

/** [PasswordEncoder.encode] is nullable only to pass a null input through; ours are never null. */
fun PasswordEncoder.hash(raw: String): String = encode(raw) ?: error("The password encoder returned no hash")
