package io.ogwars.cloud.api.auth

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/** An app holding tokens for a user: one sign-in on one device. */
data class ActiveLogin(
    val id: String,
    val clientId: String,
    val grantType: String,
    val signedInAt: Instant?,
    /** When its tokens were last issued, which a refresh renews. */
    val lastActiveAt: Instant?,
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

    /** Every authorization stored for the user, sign-ins still in progress included (D-0107). */
    fun stored(userId: UUID): List<ActiveLogin> = query(userId, completedOnly = false)

    /** The user's finished sign-ins, newest activity first (D-0213). */
    fun signedIn(userId: UUID): List<ActiveLogin> = query(userId, completedOnly = true)

    /** Which of the user's logins issued this access token, if any. */
    fun idOf(
        userId: UUID,
        accessToken: String,
    ): String? =
        jdbc
            .sql(
                "select id from oauth2_authorization where principal_name = :principal and access_token_value = :token",
            ).param("principal", userId.toString())
            .param("token", accessToken)
            .query(String::class.java)
            .optional()
            .orElse(null)

    /**
     * Ends one of the user's logins. The user's login sessions end too: one of them could sign that device in again
     * without credentials, and they are only needed while signing in.
     */
    fun revoke(
        userId: UUID,
        id: String,
    ): Boolean {
        val principal = userId.toString()
        val removed =
            jdbc
                .sql("delete from oauth2_authorization where id = :id and principal_name = :principal")
                .param("id", id)
                .param("principal", principal)
                .update() > 0
        if (removed) sessions.findByPrincipalName(principal).keys.forEach(sessions::deleteById)
        return removed
    }

    private fun query(
        userId: UUID,
        completedOnly: Boolean,
    ): List<ActiveLogin> =
        jdbc
            .sql(
                """
                select a.id, c.client_id, a.authorization_grant_type,
                    coalesce(a.authorization_code_issued_at, a.access_token_issued_at) as signed_in_at,
                    coalesce(a.refresh_token_issued_at, a.access_token_issued_at) as last_active_at,
                    coalesce(a.refresh_token_expires_at, a.access_token_expires_at) as expires_at
                from oauth2_authorization a join oauth2_registered_client c on c.id = a.registered_client_id
                where a.principal_name = :principal and (:all or a.access_token_value is not null)
                order by last_active_at desc nulls last
                """.trimIndent(),
            ).param("principal", userId.toString())
            .param("all", !completedOnly)
            .query { rs, _ ->
                ActiveLogin(
                    rs.getString("id"),
                    rs.getString("client_id"),
                    rs.getString("authorization_grant_type"),
                    rs.getTimestamp("signed_in_at")?.toInstant(),
                    rs.getTimestamp("last_active_at")?.toInstant(),
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
