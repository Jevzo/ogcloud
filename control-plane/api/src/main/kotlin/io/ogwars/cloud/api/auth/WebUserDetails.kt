package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Password login by username or email; passkey login looks users up by id, the name it stored. The principal
 * name is always the user's id, so tokens and passkeys survive a rename.
 */
@Component
class WebUserDetails(
    private val users: WebUserStore,
) : UserDetailsService {
    override fun loadUserByUsername(login: String): UserDetails {
        val user = users.findByLogin(login) ?: byId(login) ?: throw UsernameNotFoundException("No user '$login'")
        return User
            .withUsername(user.id.toString())
            // Passkey-only accounts get a hash no password matches.
            .password(user.passwordHash ?: Passwords.NO_PASSWORD)
            .disabled(!user.enabled)
            .build()
    }

    private fun byId(login: String): WebUser? =
        runCatching {
            UUID.fromString(login)
        }.getOrNull()?.let { users.find(it)?.value }
}
