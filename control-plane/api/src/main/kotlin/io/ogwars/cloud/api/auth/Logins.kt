package io.ogwars.cloud.api.auth

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** A first-party app holding tokens for a user. */
data class ActiveLogin(
    val clientId: String,
    val grantType: String,
    val issuedAt: Instant?,
    val expiresAt: Instant?,
)

/** Authorizations by user or client, plus a user's login sessions via the index; revoking a user ends both (D-0094, D-0095). */
@Component
class Logins(
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

    fun active(userId: UUID): List<ActiveLogin> =
        jdbc
            .sql(
                """
                select c.client_id, a.authorization_grant_type,
                    coalesce(a.refresh_token_issued_at, a.access_token_issued_at) as issued_at,
                    coalesce(a.refresh_token_expires_at, a.access_token_expires_at) as expires_at
                from oauth2_authorization a join oauth2_registered_client c on c.id = a.registered_client_id
                where a.principal_name = :principal
                order by issued_at desc nulls last
                """.trimIndent(),
            ).param("principal", userId.toString())
            .query { rs, _ ->
                ActiveLogin(
                    rs.getString("client_id"),
                    rs.getString("authorization_grant_type"),
                    rs.getTimestamp("issued_at")?.toInstant(),
                    rs.getTimestamp("expires_at")?.toInstant(),
                )
            }.list()

    fun revokeClient(registeredClientId: String) {
        jdbc
            .sql(
                "delete from oauth2_authorization where registered_client_id = :id",
            ).param("id", registeredClientId)
            .update()
    }
}
