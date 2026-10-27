package io.ogwars.cloud.api.permissions

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.KafkaProbe
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApiTest
class PermissionGroupsTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val players: PlayerStore,
    @Value("\${ogcloud.kafka.bootstrap-servers}") kafka: String,
) {
    private val oauth = OAuthHttp(port)
    private val probe = KafkaProbe(kafka)
    private val testUsers = TestUsers(users, passwords)
    private val admin = testUsers.token(testUsers.create(BuiltInRole.ADMIN.id))

    @Test
    fun `groups change against their version`() {
        val id = groupId()
        val created = oauth.call("POST", GROUPS, admin, create(id))
        created.statusCode() shouldBe CREATED

        val updated = oauth.call("PUT", "$GROUPS/$id", admin, update(version(created), name = "Renamed"))
        updated.statusCode() shouldBe OK
        oauth.call("PUT", "$GROUPS/$id", admin, update(version(created), name = "Again")).statusCode() shouldBe CONFLICT
        (oauth.json(oauth.call("GET", "$GROUPS/$id", admin))["settings"] as Map<*, *>)["name"] shouldBe "Renamed"
        oauth.call("POST", GROUPS, admin, create("Bad Id")).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `a new default group takes the flag, and the default cannot be deleted`() {
        val id = groupId()
        val created = oauth.call("POST", GROUPS, admin, create(id))
        try {
            oauth.call("PUT", "$GROUPS/$id", admin, update(version(created), isDefault = true)).statusCode() shouldBe OK

            (oauth.json(oauth.call("GET", "$GROUPS/default", admin))["settings"] as Map<*, *>)["defaultGroup"] shouldBe
                false
            oauth.call("DELETE", "$GROUPS/$id", admin).statusCode() shouldBe CONFLICT
        } finally {
            val seeded = oauth.call("GET", "$GROUPS/default", admin)
            oauth.call("PUT", "$GROUPS/default", admin, update(version(seeded), name = "Default", isDefault = true))
        }
        (oauth.json(oauth.call("GET", "$GROUPS/default", admin))["settings"] as Map<*, *>)["defaultGroup"] shouldBe true
    }

    @Test
    fun `deleting a group moves its holders to the default group`() {
        val id = groupId()
        oauth.call("POST", GROUPS, admin, create(id))
        val holder = UUID.randomUUID()
        players.recordSeen(holder, "Holder${holder.toString().take(4)}", Instant.now(), PlayerRank(holder, id, null))

        oauth.call("DELETE", "$GROUPS/$id", admin).statusCode() shouldBe NO_CONTENT

        players.find(holder)?.rank?.groupId shouldBe "default"
        oauth.call("GET", "$GROUPS/$id", admin).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `ranks are set for a duration or for good and announced`() {
        val id = groupId()
        oauth.call("POST", GROUPS, admin, create(id))
        val player = UUID.randomUUID()
        players.recordSeen(
            player,
            "Ranked${player.toString().take(4)}",
            Instant.now(),
            PlayerRank(player, "default", null),
        )

        val timed = oauth.json(oauth.call("PUT", "$PLAYERS/$player/rank", admin, rank(id, "1d")))
        val expiresAt = Instant.parse(timed["expiresAt"].shouldNotBeNull() as String)
        Duration.between(Instant.now(), expiresAt).toHours() shouldBe HOURS_IN_DAY - 1
        oauth.json(oauth.call("PUT", "$PLAYERS/$player/rank", admin, rank(id, "-1")))["expiresAt"] shouldBe null

        probe.received(KafkaTopics.CONFIG_CHANGED) {
            ConfigChanged.parseFrom(it).playerRank.playerUuid ==
                player.toString()
        } shouldBe
            true
        oauth.call("PUT", "$PLAYERS/$player/rank", admin, rank(id, "soon")).statusCode() shouldBe BAD_REQUEST
        oauth.call("PUT", "$PLAYERS/$player/rank", admin, rank("missing", "-1")).statusCode() shouldBe NOT_FOUND
        oauth.call("PUT", "$PLAYERS/${UUID.randomUUID()}/rank", admin, rank(id, "-1")).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `moderators cannot touch ranks`() {
        val moderator = testUsers.token(testUsers.create(BuiltInRole.MODERATOR.id))

        oauth.call("GET", GROUPS, moderator).statusCode() shouldBe FORBIDDEN
        oauth.call("PUT", "$PLAYERS/${UUID.randomUUID()}/rank", moderator, rank("default", "-1")).statusCode() shouldBe
            FORBIDDEN
    }

    private fun version(response: HttpResponse<String>) = (oauth.json(response)["version"] as Number).toLong()

    private fun groupId() = "rank-${UUID.randomUUID().toString().take(SUFFIX)}"

    private fun create(id: String) = mapOf("id" to id, "settings" to settings())

    private fun update(
        version: Long,
        name: String = "VIP",
        isDefault: Boolean = false,
    ) = mapOf("settings" to settings(name, isDefault), "version" to version)

    private fun settings(
        name: String = "VIP",
        isDefault: Boolean = false,
    ) = mapOf(
        "name" to name,
        "weight" to WEIGHT,
        "defaultGroup" to isDefault,
        "display" to mapOf("chatPrefix" to "&6VIP ", "chatSuffix" to "", "nameColor" to "&6", "tabPrefix" to "&6"),
        "permissions" to mapOf("ogcloud.fly" to "Fly in lobbies"),
    )

    private fun rank(
        groupId: String,
        duration: String,
    ) = mapOf("permissionGroupId" to groupId, "duration" to duration)

    private companion object {
        const val GROUPS = "/api/v1/permission-groups"
        const val PLAYERS = "/api/v1/players"
        const val SUFFIX = 8
        const val WEIGHT = 200
        const val HOURS_IN_DAY = 24L
        const val OK = 200
        const val CREATED = 201
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
    }
}
