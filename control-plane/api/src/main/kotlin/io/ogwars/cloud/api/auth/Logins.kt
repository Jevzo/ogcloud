package io.ogwars.cloud.api.auth

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Component
import java.util.UUID

/** Ends logins: refresh and access tokens via their authorizations, and login sessions via the index (D-0094, D-0095). */
@Component
class LoginRevocation(
    private val jdbc: JdbcClient,
    private val sessions: FindByIndexNameSessionRepository<out Session>,
) {
    fun revokeUser(userId: UUID) {
        // Users sign in with their id as the principal name (WebUserDetails).
        val principal = userId.toString()
        jdbc
            .sql(
                "delete from oauth2_authorization where principal_name = :principal",
            ).param("principal", principal)
            .update()
        sessions.findByPrincipalName(principal).keys.forEach(sessions::deleteById)
    }

    fun revokeClient(registeredClientId: String) {
        jdbc
            .sql(
                "delete from oauth2_authorization where registered_client_id = :id",
            ).param("id", registeredClientId)
            .update()
    }
}
