package io.ogwars.cloud.apitest

import io.ogwars.cloud.api.auth.FirstPartyClients
import io.ogwars.cloud.api.auth.hash
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID

/** Users for API tests, unique per call because every API test shares one database. */
class TestUsers(
    private val users: WebUserStore,
    private val passwords: PasswordEncoder,
) {
    fun create(roleId: String = BuiltInRole.VIEWER.id): WebUser {
        val id = UUID.randomUUID()
        val name = "user-${id.toString().take(SUFFIX)}"
        return WebUser(
            id,
            name,
            "$name@example.com",
            passwords.hash(PASSWORD),
            roleId,
            Instant.now(),
        ).also(users::create)
    }

    /** An access token for [user] as the authorization server would issue it, with [permissions]. */
    fun token(
        user: WebUser,
        permissions: Collection<ApiPermission> = BuiltInRole.find(user.roleId)?.permissions.orEmpty(),
    ): String =
        TestSigningKeys.accessToken(
            TestSigningKeys.key(TestSigningKeys.CURRENT),
            permissions.map {
                it.scope
            },
            "user:${user.id}",
            // As the dashboard's tokens are; the API takes users' tokens only for its apps (D-0185).
            audience = FirstPartyClients.WEB,
        )

    companion object {
        const val PASSWORD = "correct horse battery staple"
        private const val SUFFIX = 8
    }
}
