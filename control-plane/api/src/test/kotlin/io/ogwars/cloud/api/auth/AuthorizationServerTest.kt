package io.ogwars.cloud.api.auth

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TestSigningKeys
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import io.ogwars.cloud.livestate.ValkeyConnection
import io.ogwars.cloud.livestate.ValkeyKeys
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.jwk.JWKSet
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import java.util.UUID

@ApiTest
class AuthorizationServerTest(
    @LocalServerPort port: Int,
    @Autowired private val users: WebUserStore,
    @Autowired private val passwords: PasswordEncoder,
    @Autowired private val clients: RegisteredClientRepository,
    @Autowired private val decoder: JwtDecoder,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val valkey: ValkeyConnection,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val verifier = "a-pkce-verifier-that-is-long-enough-for-rfc-7636-${UUID.randomUUID()}"

    @Test
    fun `the dashboard logs in with PKCE and its token carries the role's permissions`() {
        val user = user(BuiltInRole.MODERATOR)

        val tokens = login(user)

        val jwt = decoder.decode(tokens.accessToken)
        jwt.headers["kid"] shouldBe TestSigningKeys.CURRENT
        jwt.headers["typ"] shouldBe "at+jwt"
        jwt.issuer.toString() shouldBe TEST_ISSUER
        jwt.subject shouldBe user.id.toString()
        jwt.getClaimAsString(TokenClaims.ACTOR_CLAIM) shouldBe "user:${user.id}"
        jwt.getClaimAsStringList(TokenClaims.PERMISSIONS_CLAIM) shouldContainExactly
            BuiltInRole.MODERATOR.permissions
                .map { it.scope }
                .sorted()
        tokens.refreshToken.shouldNotBeNull()
        tokens.idToken.shouldNotBeNull()
        // The login session lives in Valkey, so any replica can finish the flow (D-0091).
        valkey.sync().keys("${ValkeyKeys.WEB_SESSION_NAMESPACE}:sessions:*").shouldNotBeEmpty()
    }

    @Test
    fun `refresh tokens rotate, and presenting a rotated one ends the whole login`() {
        val first = login(user(BuiltInRole.VIEWER))

        val second = oauth.tokens(oauth.refresh(FirstPartyClients.WEB, refreshOf(first)))
        second.refreshToken shouldNotBe first.refreshToken

        val replay = oauth.refresh(FirstPartyClients.WEB, refreshOf(first))
        replay.statusCode() shouldBe BAD_REQUEST
        oauth.error(replay) shouldBe "invalid_grant"
        oauth.refresh(FirstPartyClients.WEB, refreshOf(second)).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `a revoked refresh token cannot be used`() {
        val tokens = login(user(BuiltInRole.VIEWER))

        oauth.revoke(FirstPartyClients.WEB, refreshOf(tokens)).statusCode() shouldBe OK

        oauth.refresh(FirstPartyClients.WEB, refreshOf(tokens)).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `a disabled user cannot refresh`() {
        val user = user(BuiltInRole.ADMIN)
        val tokens = login(user)

        jdbc.sql("update web_users set enabled = false where id = :id").param("id", user.id).update()

        oauth.error(oauth.refresh(FirstPartyClients.WEB, refreshOf(tokens))) shouldBe "invalid_grant"
    }

    @Test
    fun `the desktop app may receive the code on any loopback port`() {
        val user = user(BuiltInRole.VIEWER)
        val redirect = "http://127.0.0.1:49152/callback"

        val callback = oauth.authorize(FirstPartyClients.DESKTOP, redirect, user.username, PASSWORD, verifier)

        callback.toString() shouldStartWith redirect
        oauth
            .exchange(
                FirstPartyClients.DESKTOP,
                oauth.codeFrom(callback),
                redirect,
                verifier,
            ).refreshToken
            .shouldNotBeNull()
    }

    @Test
    fun `apps must use PKCE`() {
        val callback = oauth.authorize(FirstPartyClients.WEB, WEB_REDIRECT, "nobody", PASSWORD, verifier = null)

        OAuthHttp.query(callback)["error"] shouldBe "invalid_request"
    }

    @Test
    fun `a confidential client cannot refresh without its secret`() {
        val client = machineClient(listOf("groups:read"))

        oauth.error(oauth.refresh(client.clientId, "whatever")) shouldBe "invalid_client"
    }

    @Test
    fun `machine clients get their scopes as permissions`() {
        val client = machineClient(listOf("groups:read", "servers:command"))

        val token =
            oauth.tokens(
                oauth.clientCredentials(client.clientId, SECRET, listOf("groups:read", "servers:command")),
            )

        val jwt = decoder.decode(token.accessToken)
        jwt.getClaimAsString(TokenClaims.ACTOR_CLAIM) shouldBe "client:${client.clientId}"
        jwt.getClaimAsStringList(TokenClaims.PERMISSIONS_CLAIM) shouldContainExactly
            listOf("groups:read", "servers:command")
        token.refreshToken shouldBe null
    }

    @Test
    fun `tokens of a key still published verify after rotation, others never`() {
        val keys = JWKSet.parse(oauth.get("/oauth2/jwks").body()).keys.map { it.keyID }
        keys shouldContainExactly listOf(TestSigningKeys.OLD, TestSigningKeys.CURRENT)

        val old = TestSigningKeys.accessToken(TestSigningKeys.key(TestSigningKeys.OLD), listOf("servers:read"))
        val stranger =
            TestSigningKeys.accessToken(
                SigningKeys.parse("stranger", TestSigningKeys.pem(TestSigningKeys.rsa())),
                emptyList(),
            )
        val idTokenType =
            TestSigningKeys.accessToken(
                TestSigningKeys.key(TestSigningKeys.CURRENT),
                emptyList(),
                type = JOSEObjectType.JWT,
            )
        val foreignIssuer =
            TestSigningKeys.accessToken(
                TestSigningKeys.key(TestSigningKeys.CURRENT),
                emptyList(),
                issuer = "https://elsewhere.example",
            )

        oauth.getWithToken(PROBE, old).statusCode() shouldBe OK
        oauth.getWithToken(PROBE, stranger).statusCode() shouldBe UNAUTHORIZED
        oauth.getWithToken(PROBE, idTokenType).statusCode() shouldBe UNAUTHORIZED
        oauth.getWithToken(PROBE, foreignIssuer).statusCode() shouldBe UNAUTHORIZED
        oauth.get(PROBE).statusCode() shouldBe UNAUTHORIZED
    }

    @Test
    fun `an ID token is no access token`() {
        val tokens = login(user(BuiltInRole.OWNER))

        oauth.getWithToken(PROBE, tokens.idToken ?: error("no ID token")).statusCode() shouldBe UNAUTHORIZED
        oauth.getWithToken(PROBE, tokens.accessToken).statusCode() shouldBe OK
    }

    @Test
    fun `discovery names the public origin and the routed endpoints`() {
        val discovery = oauth.get("/.well-known/openid-configuration").body()

        discovery.contains("\"issuer\":\"$TEST_ISSUER\"") shouldBe true
        discovery.contains("\"userinfo_endpoint\":\"$TEST_ISSUER/oauth2/userinfo\"") shouldBe true
        discovery.contains("\"end_session_endpoint\":\"$TEST_ISSUER/oauth2/logout\"") shouldBe true
    }

    private fun login(user: WebUser): OAuthHttp.Tokens {
        val callback = oauth.authorize(FirstPartyClients.WEB, WEB_REDIRECT, user.username, PASSWORD, verifier)
        OAuthHttp.query(callback)["state"] shouldBe OAuthHttp.STATE
        return oauth.exchange(FirstPartyClients.WEB, oauth.codeFrom(callback), WEB_REDIRECT, verifier)
    }

    private fun refreshOf(tokens: OAuthHttp.Tokens) = tokens.refreshToken ?: error("no refresh token")

    private fun user(role: BuiltInRole): WebUser = testUsers.create(role.id)

    private fun machineClient(scopes: List<String>): RegisteredClient {
        val id = "machine-${UUID.randomUUID().toString().take(SUFFIX)}"
        return RegisteredClient
            .withId(id)
            .clientId(id)
            .clientSecret("{noop}$SECRET")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scopes { it.addAll(scopes) }
            .build()
            .also(clients::save)
    }

    private companion object {
        const val PASSWORD = TestUsers.PASSWORD
        const val SECRET = "machine-secret"
        const val WEB_REDIRECT = "$TEST_ISSUER/auth/callback"
        const val PROBE = "/api/v1/probes/client"
        const val SUFFIX = 8
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
    }
}
