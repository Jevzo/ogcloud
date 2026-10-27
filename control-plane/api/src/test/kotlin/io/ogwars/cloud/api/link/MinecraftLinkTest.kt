package io.ogwars.cloud.api.link

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.KafkaProbe
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.contracts.events.v1.LinkCodeIssued
import io.ogwars.cloud.contracts.events.v1.PlayerActivity
import io.ogwars.cloud.contracts.events.v1.PlayerConnected
import io.ogwars.cloud.domain.audit.Actor
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.domain.user.WebUser
import io.ogwars.cloud.messaging.EventMetadataFactory
import io.ogwars.cloud.messaging.EventPublisher
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/** Minecraft account links with codes shown in game, and usernames that follow the Minecraft name (D-0106). */
@ApiTest
class MinecraftLinkTest(
    @LocalServerPort port: Int,
    @Autowired private val users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val players: PlayerStore,
    @Autowired private val sessions: SessionStore,
    @Autowired private val publisher: EventPublisher,
    @Autowired private val metadata: EventMetadataFactory,
    @Value("\${ogcloud.kafka.bootstrap-servers}") kafka: String,
) {
    private val oauth = OAuthHttp(port)
    private val probe = KafkaProbe(kafka)
    private val testUsers = TestUsers(users, passwords)

    @Test
    fun `a code shown in game links the account once, and the username follows the Minecraft name`() {
        val user = testUsers.create()
        val token = testUsers.token(user)
        val (player, name) = online()

        val sent = oauth.call("POST", LINK, token, mapOf("playerName" to name))
        sent.statusCode() shouldBe ACCEPTED
        oauth.json(sent)["playerUuid"] shouldBe player.toString()
        val code = codeFor(player)
        oauth.call("POST", CONFIRM, token, mapOf("code" to otherThan(code))).statusCode() shouldBe BAD_REQUEST
        oauth.call("POST", CONFIRM, token, mapOf("code" to code)).statusCode() shouldBe BAD_REQUEST

        oauth.call("POST", LINK, token, mapOf("playerName" to name))
        val me = oauth.json(oauth.call("POST", CONFIRM, token, mapOf("code" to codeFor(player, except = code))))

        me["linkedPlayerUuid"] shouldBe player.toString()
        me["linkedPlayerName"] shouldBe name
        me["username"] shouldBe name
        val renamed = "${name}x"
        connect(player, renamed)
        runBlocking { eventually(WAIT) { users.find(user.id)?.value?.username shouldBe renamed } }
    }

    @Test
    fun `only online players get codes, and an account links to one user`() {
        val (player, name) = online()
        linked(BuiltInRole.VIEWER.id, player)
        val other = testUsers.token(testUsers.create())

        oauth.call("POST", LINK, other, mapOf("playerName" to "nobody-online")).statusCode() shouldBe NOT_FOUND
        oauth.call("POST", LINK, other, mapOf("playerName" to name)).statusCode() shouldBe ACCEPTED
        oauth.call("POST", CONFIRM, other, mapOf("code" to codeFor(player))).statusCode() shouldBe CONFLICT
    }

    @Test
    fun `user managers unlink others but never owners, owners unlink themselves`() {
        val (ownerPlayer, ownerName) = online()
        val owner = linked(BuiltInRole.OWNER.id, ownerPlayer)
        val viewer = linked(BuiltInRole.VIEWER.id, online().first)
        val manager = testUsers.token(testUsers.create(), listOf(ApiPermission.USERS_WRITE))
        val ownerToken = testUsers.token(owner)

        oauth.json(oauth.call("GET", "$USERS/${owner.id}", ownerToken))["linkedPlayerName"] shouldBe ownerName
        oauth.call("DELETE", "$USERS/${owner.id}/minecraft-link", manager).statusCode() shouldBe FORBIDDEN
        oauth.call("DELETE", "$USERS/${viewer.id}/minecraft-link", manager).statusCode() shouldBe NO_CONTENT
        oauth.call("DELETE", LINK, ownerToken).statusCode() shouldBe NO_CONTENT

        users.find(viewer.id)?.value?.linkedPlayerUuid shouldBe null
        users.find(owner.id)?.value?.linkedPlayerUuid shouldBe null
    }

    /** A player who is online right now, under a fresh name. */
    private fun online(): Pair<UUID, String> {
        val uuid = UUID.randomUUID()
        val name = "mc${uuid.toString().take(SUFFIX)}"
        players.recordSeen(uuid, name, Instant.now(), PlayerRank(uuid, DEFAULT_GROUP, null))
        sessions.put(PlayerSession(uuid, name, UUID.randomUUID(), null, DEFAULT_GROUP, 0, Instant.now()))
        return uuid to name
    }

    private fun linked(
        roleId: String,
        player: UUID,
    ): WebUser {
        val user = testUsers.create(roleId)
        users.update(
            users.find(user.id)?.revision ?: error("User ${user.id} vanished"),
            user.copy(linkedPlayerUuid = player),
        )
        return user
    }

    private fun codeFor(
        player: UUID,
        except: String? = null,
    ): String {
        var code: String? = null
        probe.received(KafkaTopics.ACCOUNT_LINK) { record ->
            val issued = LinkCodeIssued.parseFrom(record)
            (issued.playerUuid == player.toString() && issued.code != except).also { if (it) code = issued.code }
        }
        return code ?: error("No link code reached $player")
    }

    private fun connect(
        player: UUID,
        name: String,
    ) = publisher.publishAndWait(
        KafkaTopics.PLAYER_ACTIVITY,
        player.toString(),
        PlayerActivity
            .newBuilder()
            .setMetadata(metadata.create(Actor.system("test")))
            .setPlayerUuid(player.toString())
            .setConnected(PlayerConnected.newBuilder().setPlayerName(name).setProxyId(UUID.randomUUID().toString()))
            .build(),
    )

    private fun otherThan(code: String) = ((code.toInt() + 1) % CODE_BOUND).toString().padStart(code.length, '0')

    private companion object {
        const val LINK = "/api/v1/me/minecraft-link"
        const val CONFIRM = "$LINK/confirm"
        const val USERS = "/api/v1/users"
        const val DEFAULT_GROUP = "default"
        const val SUFFIX = 8
        const val CODE_BOUND = 1_000_000
        const val ACCEPTED = 202
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
        val WAIT = 30.seconds
    }
}
