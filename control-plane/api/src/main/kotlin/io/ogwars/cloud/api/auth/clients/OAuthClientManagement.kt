package io.ogwars.cloud.api.auth.clients

import io.ogwars.cloud.api.auth.AuthProperties
import io.ogwars.cloud.api.auth.Logins
import io.ogwars.cloud.api.auth.Owners
import io.ogwars.cloud.api.auth.hash
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.common.IdGenerator
import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.service.SecretGenerator
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class OAuthClientView(
    val clientId: String,
    val name: String,
    val scopes: List<String>,
    /** The dashboard, desktop and mobile apps; OgCloud manages them itself. */
    val firstParty: Boolean,
    val createdAt: Instant,
)

/** A machine client's credentials; the secret is shown this once and stored only as a bcrypt hash. */
data class OAuthClientCredentials(
    val clientId: String,
    val clientSecret: String,
    val name: String,
    val scopes: List<String>,
)

/** Machine clients with client credentials (D-0040); their scopes are permissions, set by owners only (D-0094). */
@Service
class OAuthClientManagement(
    private val clients: RegisteredClientRepository,
    private val jdbc: JdbcClient,
    private val owners: Owners,
    private val logins: Logins,
    private val passwords: PasswordEncoder,
    private val secrets: SecretGenerator,
    private val ids: IdGenerator,
    private val properties: AuthProperties,
) {
    fun list(): List<OAuthClientView> =
        jdbc
            .sql("select client_id from oauth2_registered_client order by client_id_issued_at, client_id")
            .query { rs, _ -> rs.getString("client_id") }
            .list()
            .mapNotNull { clients.findByClientId(it)?.toView() }

    fun create(
        actor: Actor,
        name: String,
        scopes: Collection<String>,
    ): OAuthClientCredentials {
        owners.requireOwner(actor)
        val id = ids.next().toString()
        val secret = secrets.next()
        val client =
            RegisteredClient
                .withId(id)
                .clientId(id)
                .clientName(name.requireName())
                .clientSecret(passwords.hash(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes { it.addAll(scopes.requirePermissions()) }
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(properties.tokens.accessToken).build())
                .build()
        clients.save(client)
        return client.credentials(secret)
    }

    fun update(
        actor: Actor,
        clientId: String,
        name: String,
        scopes: Collection<String>,
    ): OAuthClientView {
        owners.requireOwner(actor)
        val updated =
            RegisteredClient
                .from(machine(clientId))
                .clientName(name.requireName())
                .scopes {
                    it.clear()
                    it.addAll(scopes.requirePermissions())
                }.build()
        clients.save(updated)
        return updated.toView()
    }

    /** Issues a new secret; tokens issued with the old one stop working. */
    fun rotateSecret(clientId: String): OAuthClientCredentials {
        val secret = secrets.next()
        val rotated = RegisteredClient.from(machine(clientId)).clientSecret(passwords.hash(secret)).build()
        clients.save(rotated)
        logins.revokeClient(rotated.id)
        return rotated.credentials(secret)
    }

    @Transactional
    fun delete(clientId: String) {
        val client = machine(clientId)
        logins.revokeClient(client.id)
        jdbc
            .sql(
                "delete from oauth2_authorization_consent where registered_client_id = :id",
            ).param("id", client.id)
            .update()
        jdbc.sql("delete from oauth2_registered_client where id = :id").param("id", client.id).update()
    }

    private fun machine(clientId: String): RegisteredClient {
        val client =
            clients.findByClientId(clientId) ?: throw NoSuchElementException("OAuth client $clientId does not exist")
        if (client.isFirstParty()) throw ConflictException("OgCloud manages its own apps' clients")
        return client
    }

    private fun RegisteredClient.isFirstParty() = ClientAuthenticationMethod.NONE in clientAuthenticationMethods

    private fun RegisteredClient.toView() =
        OAuthClientView(
            clientId,
            clientName,
            scopes.sorted(),
            isFirstParty(),
            clientIdIssuedAt ?: Instant.EPOCH,
        )

    private fun RegisteredClient.credentials(secret: String) =
        OAuthClientCredentials(clientId, secret, clientName, scopes.sorted())

    private fun String.requireName(): String = trim().also { require(it.isNotEmpty()) { "A client needs a name" } }

    private fun Collection<String>.requirePermissions(): Set<String> {
        require(isNotEmpty()) { "A client needs at least one permission" }
        return ApiPermission.parse(this).map { it.scope }.toSet()
    }
}
