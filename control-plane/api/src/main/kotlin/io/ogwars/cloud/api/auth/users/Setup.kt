package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.auth.AuthProperties
import io.ogwars.cloud.api.auth.hash
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.common.sha256Hex
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.PasswordPolicy
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock

/** A fresh install's first owner, created with the setup token from Infisical (D-0094). */
@Service
class Setup(
    private val properties: AuthProperties,
    private val users: WebUserStore,
    private val passwords: PasswordEncoder,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    fun pending(): Boolean = properties.setupToken != null && !users.ownerExists()

    fun complete(
        setupToken: String,
        username: String,
        email: String,
        password: String,
    ): WebUser {
        val expected = properties.setupToken ?: throw ConflictException("This installation has no setup token")
        // Comparing digests keeps the comparison constant-time whatever the input's length.
        if (!MessageDigest.isEqual(sha256Hex(setupToken).toByteArray(), sha256Hex(expected).toByteArray())) {
            throw AccessDeniedException("Wrong setup token")
        }
        PasswordPolicy.validate(password)
        val owner =
            WebUser(ids.next(), username, email, passwords.hash(password), BuiltInRole.OWNER.id, clock.instant())
        if (!users.createFirstOwner(owner)) throw ConflictException("Setup is already complete")
        return owner
    }
}
