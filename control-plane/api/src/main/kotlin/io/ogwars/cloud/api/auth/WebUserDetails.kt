package io.ogwars.cloud.api.auth

import io.ogwars.cloud.domain.store.WebUserStore
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component

/** Password login by username or email. The principal name is the user's id, so tokens survive a rename. */
@Component
class WebUserDetails(
    private val users: WebUserStore,
) : UserDetailsService {
    override fun loadUserByUsername(login: String): UserDetails {
        val user = users.findByLogin(login) ?: throw UsernameNotFoundException("No user '$login'")
        // Passkey-only accounts have no password to check.
        val passwordHash = user.passwordHash ?: throw UsernameNotFoundException("User '$login' has no password")
        return User
            .withUsername(user.id.toString())
            .password(passwordHash)
            .disabled(!user.enabled)
            .build()
    }
}
