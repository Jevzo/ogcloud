package io.ogwars.cloud.api.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class LengthSafeBcryptPasswordEncoder : PasswordEncoder {
    private val delegate = BCryptPasswordEncoder()

    override fun encode(rawPassword: CharSequence): String = delegate.encode(preHash(rawPassword))

    override fun matches(
        rawPassword: CharSequence,
        encodedPassword: String,
    ): Boolean = encodedPassword.isNotBlank() && delegate.matches(preHash(rawPassword), encodedPassword)

    private fun preHash(rawPassword: CharSequence): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashed = digest.digest(rawPassword.toString().toByteArray(StandardCharsets.UTF_8))
        return hashed.joinToString(separator = "") { "%02x".format(it) }
    }
}
