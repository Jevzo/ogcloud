package io.ogwars.cloud.api.privacy

import io.ogwars.cloud.api.auth.FirstPartyClients
import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TEST_ISSUER
import io.ogwars.cloud.apitest.TestPasskeys
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.audit.ActorType
import io.ogwars.cloud.domain.audit.AuditEntry
import io.ogwars.cloud.domain.job.Job
import io.ogwars.cloud.domain.job.JobState
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.privacy.Pseudonymizer
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.JobStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ReadModelKind
import io.ogwars.cloud.domain.store.ReadModelStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.Base32
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.Totp
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import java.time.Instant
import java.util.UUID

/** Export and erasure leave nothing that names the person (D-0049, D-0107). */
@ApiTest
class PrivacyTest(
    @LocalServerPort port: Int,
    @Autowired private val users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val players: PlayerStore,
    @Autowired private val sessions: SessionStore,
    @Autowired private val readModels: ReadModelStore,
    @Autowired private val audit: AuditLogStore,
    @Autowired private val jobs: JobStore,
    @Autowired private val pseudonymizer: Pseudonymizer,
    @Autowired private val jdbc: JdbcClient,
    @Autowired private val loginSessions: FindByIndexNameSessionRepository<out Session>,
    @Autowired entities: PublicKeyCredentialUserEntityRepository,
    @Autowired credentials: UserCredentialRepository,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val passkeys = TestPasskeys(entities, credentials)
    private val owner = testUsers.token(testUsers.create(BuiltInRole.OWNER.id))

    @Test
    fun `a player's export shows what is stored, and the erasure leaves no trace`() {
        val uuid = UUID.randomUUID()
        val name = "mc${uuid.toString().take(SUFFIX)}"
        players.recordSeen(uuid, name, Instant.now(), PlayerRank(uuid, DEFAULT_GROUP, null))
        val linked = testUsers.create()
        users.update(users.find(linked.id)?.revision ?: error("User vanished"), linked.copy(linkedPlayerUuid = uuid))
        audit.append(
            AuditEntry(UUID.randomUUID(), Instant.now(), Actor.system("test"), "PUT /x", "players", uuid.toString()),
        )
        readModels.put(ReadModelKind.PLAYER_PROFILE, uuid.toString(), byteArrayOf(1))

        val export = oauth.json(oauth.call("GET", "$PLAYERS/$uuid", owner))
        (export["player"] as Map<*, *>)["name"] shouldBe name
        export["linkedUserId"] shouldBe linked.id.toString()
        (export["auditEntries"] as List<*>).size shouldBe 1

        oauth.call("POST", "$PLAYERS/$uuid/erasure", owner).statusCode() shouldBe NO_CONTENT

        players.find(uuid) shouldBe null
        readModels.get(ReadModelKind.PLAYER_PROFILE, uuid.toString()) shouldBe null
        users.find(linked.id)?.value?.linkedPlayerUuid shouldBe null
        traces(uuid) shouldBe 0
        audit.about(pseudonymizer.pseudonym(uuid.toString())).map { it.action } shouldContain
            "POST /api/v1/privacy/players/{uuid}/erasure"
        oauth.call("POST", "$PLAYERS/$uuid/erasure", owner).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `online players are not erased`() {
        val uuid = UUID.randomUUID()
        players.recordSeen(uuid, "online", Instant.now(), PlayerRank(uuid, DEFAULT_GROUP, null))
        sessions.put(PlayerSession(uuid, "online", UUID.randomUUID(), null, DEFAULT_GROUP, 0, Instant.now()))

        oauth.call("POST", "$PLAYERS/$uuid/erasure", owner).statusCode() shouldBe CONFLICT
        players.find(uuid)?.uuid shouldBe uuid
    }

    @Test
    fun `a user's export shows what is stored, and the erasure leaves no trace`() {
        val user = testUsers.create()
        val actor = Actor(ActorType.USER, user.id.toString())
        audit.append(AuditEntry(UUID.randomUUID(), Instant.now(), actor, "POST /x", "x", "lobby"))
        jobs.create(job(actor))
        val login = signIn(user)
        enrollTotp(user)
        val passkeyEntity = passkeys.register(user.id)

        val export = oauth.json(oauth.call("GET", "$USERS/${user.id}", owner))
        (export["user"] as Map<*, *>)["email"] shouldBe user.email
        export["totpEnabled"] shouldBe true
        (export["passkeys"] as List<*>).size shouldBe 1
        (export["logins"] as List<*>).map { (it as Map<*, *>)["clientId"] } shouldContain FirstPartyClients.WEB
        (export["auditEntries"] as List<*>).map { (it as Map<*, *>)["action"] } shouldContainExactlyInAnyOrder
            listOf("POST /x", "POST /api/v1/me/totp", "POST /api/v1/me/totp/confirm")

        oauth.call("POST", "$USERS/${user.id}/erasure", owner).statusCode() shouldBe NO_CONTENT

        users.find(user.id) shouldBe null
        traces(user.id) shouldBe 0
        oauth.error(oauth.refresh(FirstPartyClients.WEB, login.refreshToken ?: error("no refresh token"))) shouldBe
            "invalid_grant"
        loginSessions.findByPrincipalName(user.id.toString()).keys.shouldBeEmpty()
        jdbc
            .sql("select count(*) from user_credentials where user_entity_user_id = :id")
            .param("id", passkeyEntity)
            .query(Long::class.java)
            .single() shouldBe 0
    }

    @Test
    fun `only owners erase owners, and an owner who erases themselves is not named by the entry`() {
        val first = testUsers.create(BuiltInRole.OWNER.id)
        testUsers.create(BuiltInRole.OWNER.id)
        val eraser = testUsers.token(testUsers.create(), listOf(ApiPermission.PRIVACY_ERASE))

        oauth.call("POST", "$USERS/${first.id}/erasure", eraser).statusCode() shouldBe FORBIDDEN
        oauth.call("POST", "$USERS/${first.id}/erasure", testUsers.token(first)).statusCode() shouldBe NO_CONTENT

        traces(first.id) shouldBe 0
        val pseudonym = pseudonymizer.pseudonym(first.id.toString())
        audit.about(pseudonym).single().actor shouldBe Actor(ActorType.USER, pseudonym)
    }

    private fun signIn(user: WebUser): OAuthHttp.Tokens {
        val verifier = "verifier-${UUID.randomUUID()}-${UUID.randomUUID()}"
        val callback = oauth.authorize(FirstPartyClients.WEB, WEB_REDIRECT, user.username, TestUsers.PASSWORD, verifier)
        return oauth.exchange(FirstPartyClients.WEB, oauth.codeFrom(callback), WEB_REDIRECT, verifier)
    }

    private fun enrollTotp(user: WebUser) {
        val token = testUsers.token(user)
        val secret = Base32.decode(oauth.json(oauth.call("POST", "/api/v1/me/totp", token))["secret"] as String)
        val code = Totp().let { it.code(secret, it.step(Instant.now())) }
        oauth.call("POST", "/api/v1/me/totp/confirm", token, mapOf("code" to code)).statusCode() shouldBe NO_CONTENT
    }

    /** Rows anywhere a person's data could be that still contain [id]. */
    private fun traces(id: UUID): Long =
        TABLES.sumOf { table ->
            jdbc
                .sql(
                    "select count(*) from $table t where t::text like :id",
                ).param("id", "%$id%")
                .query(Long::class.java)
                .single()
        }

    private fun job(actor: Actor) =
        Job(
            id = UUID.randomUUID(),
            kind = "group-restart",
            state = JobState.SUCCEEDED,
            payload = emptyMap(),
            progress = emptyMap(),
            completedSteps = 1,
            totalSteps = 1,
            message = "",
            createdBy = actor,
            createdAt = Instant.now(),
        )

    private companion object {
        const val PLAYERS = "/api/v1/privacy/players"
        const val WEB_REDIRECT = "$TEST_ISSUER/auth/callback"
        const val USERS = "/api/v1/privacy/users"
        const val DEFAULT_GROUP = "default"
        const val SUFFIX = 8
        const val NO_CONTENT = 204
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
        val TABLES =
            listOf(
                "players",
                "web_users",
                "web_user_totp",
                "user_entities",
                "oauth2_authorization",
                "oauth2_authorization_consent",
                "audit_log",
                "jobs",
                "template_versions",
            )
    }
}
