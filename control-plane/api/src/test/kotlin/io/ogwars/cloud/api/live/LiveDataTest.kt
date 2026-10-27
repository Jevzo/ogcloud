package io.ogwars.cloud.api.live

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.KafkaProbe
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.contracts.events.v1.ServerCommand
import io.ogwars.cloud.contracts.events.v1.TransferRequested
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.server.ServerState
import io.ogwars.cloud.domain.server.server
import io.ogwars.cloud.domain.session.PlayerSession
import io.ogwars.cloud.domain.store.CrashFailureStore
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.PlayerStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.SessionStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ApiTest
class LiveDataTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val groups: GroupStore,
    @Autowired private val servers: ServerStateStore,
    @Autowired private val sessions: SessionStore,
    @Autowired private val players: PlayerStore,
    @Autowired private val failures: CrashFailureStore,
    @Autowired private val jdbc: JdbcClient,
    @Value("\${ogcloud.kafka.bootstrap-servers}") kafka: String,
) {
    private val oauth = OAuthHttp(port)
    private val probe = KafkaProbe(kafka)
    private val testUsers = TestUsers(users, passwords)
    private val admin = testUsers.token(testUsers.create(BuiltInRole.ADMIN.id))
    private val groupId = "live-${UUID.randomUUID().toString().take(SUFFIX)}"

    init {
        groups.create(group(id = groupId))
    }

    @Test
    fun `servers are listed from the live state and filtered`() {
        val running = servers.create(server(group = groupId, id = UUID.randomUUID()))
        servers.create(
            server(group = groupId, id = UUID.randomUUID(), state = ServerState.STARTING),
        )

        val listed = oauth.jsonList(oauth.call("GET", "$SERVERS?groupId=$groupId&state=RUNNING", admin))

        running shouldBe true
        listed.size shouldBe 1
        oauth.call("GET", "$SERVERS/${UUID.randomUUID()}", admin).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `a server request waits for the EULA, then reaches the controller`() {
        jdbc.sql("update network_settings set eula_accepted_at = null").update()
        oauth.call("POST", SERVERS, admin, mapOf("groupId" to groupId)).statusCode() shouldBe CONFLICT

        jdbc.sql("update network_settings set eula_accepted_at = now()").update()
        val requested = oauth.call("POST", SERVERS, admin, mapOf("groupId" to groupId))

        requested.statusCode() shouldBe ACCEPTED
        val serverId = oauth.json(requested)["serverId"] as String
        probe.received(KafkaTopics.SERVER_COMMAND) {
            ServerCommand.parseFrom(it).let { c ->
                c.serverId == serverId &&
                    c.hasRequest()
            }
        } shouldBe
            true
    }

    @Test
    fun `stops, kills and template pushes go to existing servers only`() {
        val backend = UUID.randomUUID()
        val proxy = UUID.randomUUID()
        servers.create(server(group = groupId, id = backend))
        servers.create(server(group = groupId, id = proxy, groupType = GroupType.PROXY))

        oauth.call("POST", "$SERVERS/$backend/stop", admin).statusCode() shouldBe ACCEPTED
        oauth.call("POST", "$SERVERS/$backend/kill", admin).statusCode() shouldBe ACCEPTED
        oauth.call("POST", "$SERVERS/$backend/template-push", admin).statusCode() shouldBe ACCEPTED
        oauth.call("POST", "$SERVERS/$proxy/template-push", admin).statusCode() shouldBe CONFLICT
        oauth.call("POST", "$SERVERS/${UUID.randomUUID()}/stop", admin).statusCode() shouldBe NOT_FOUND
        probe.received(KafkaTopics.SERVER_COMMAND) {
            ServerCommand.parseFrom(it).let { c ->
                c.serverId ==
                    backend.toString() &&
                    c.hasKill()
            }
        } shouldBe
            true
    }

    @Test
    fun `console commands name a valid target`() {
        oauth.call("POST", COMMANDS, admin, command("ALL", null, "say hi")).statusCode() shouldBe ACCEPTED
        oauth.call("POST", COMMANDS, admin, command("GROUP", groupId, "/say hi")).statusCode() shouldBe ACCEPTED
        oauth.call("POST", COMMANDS, admin, command("SERVER", null, "say hi")).statusCode() shouldBe BAD_REQUEST
        oauth.call("POST", COMMANDS, admin, command("GROUP", "unknown-group", "say hi")).statusCode() shouldBe NOT_FOUND
        oauth.call("POST", COMMANDS, admin, command("ALL", null, " / ")).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `online and known players are listed in pages`() {
        val ids = (1..3).map { UUID.randomUUID() }.sorted()
        ids.forEachIndexed { index, uuid -> online(uuid, "Pager${index}x${uuid.toString().take(4)}") }

        val first = oauth.json(oauth.call("GET", "$PLAYERS?limit=1", admin))
        val known = oauth.json(oauth.call("GET", "$PLAYERS/known?name=pager1x&limit=10", admin))

        (first["items"] as List<*>).size shouldBe 1
        (known["items"] as List<*>).map { (it as Map<*, *>)["uuid"] } shouldContainExactly listOf(ids[1].toString())
        val profile = oauth.json(oauth.call("GET", "$PLAYERS/${ids[0]}", admin))
        (profile["session"] as Map<*, *>)["permissionGroupId"] shouldBe "default"
    }

    @Test
    fun `transfers need an online player and exactly one destination`() {
        val player = UUID.randomUUID()
        online(player, "Mover${player.toString().take(6)}")
        val offline = UUID.randomUUID()

        oauth.call("POST", "$PLAYERS/$player/transfer", admin, transfer(groupId = groupId)).statusCode() shouldBe
            ACCEPTED
        oauth
            .call(
                "POST",
                "$PLAYERS/$player/transfer",
                admin,
                transfer(groupId = groupId, kick = "bye"),
            ).statusCode() shouldBe
            BAD_REQUEST
        oauth.call("POST", "$PLAYERS/$offline/transfer", admin, transfer(kick = "bye")).statusCode() shouldBe CONFLICT
        probe.received(
            KafkaTopics.PLAYER_TRANSFER,
        ) { TransferRequested.parseFrom(it).playerUuid == player.toString() } shouldBe
            true
    }

    @Test
    fun `search shows only the sections the caller may read`() {
        val overviewOnly = testUsers.token(testUsers.create(), listOf(ApiPermission.OVERVIEW_READ))

        val full = oauth.json(oauth.call("GET", "$SEARCH?q=${groupId.take(SEARCH_LENGTH)}", admin))
        val narrow = oauth.json(oauth.call("GET", "$SEARCH?q=${groupId.take(SEARCH_LENGTH)}", overviewOnly))

        full["groups"] as List<*> shouldContain groupId
        narrow["groups"] shouldBe null
        narrow["players"] shouldBe null
        oauth.call("GET", "$SEARCH?q=x", admin).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `a paused group is resumed`() {
        failures.record(GroupId(groupId), UUID.randomUUID(), Instant.now(), Duration.ofMinutes(10))
        failures.pause(GroupId(groupId))

        oauth.json(oauth.call("GET", "$GROUPS/$groupId/crash-loop", admin))["paused"] shouldBe true
        val resumed = oauth.json(oauth.call("POST", "$GROUPS/$groupId/crash-loop/resume", admin))

        resumed["paused"] shouldBe false
        resumed["failures"] shouldBe emptyList<Any>()
    }

    @Test
    fun `viewers watch but do not command`() {
        val viewer = testUsers.token(testUsers.create(BuiltInRole.VIEWER.id))

        oauth.call("GET", SERVERS, viewer).statusCode() shouldBe OK
        oauth.call("POST", SERVERS, viewer, mapOf("groupId" to groupId)).statusCode() shouldBe FORBIDDEN
        oauth.call("POST", COMMANDS, viewer, command("ALL", null, "stop")).statusCode() shouldBe FORBIDDEN
    }

    private fun online(
        uuid: UUID,
        name: String,
    ) {
        players.recordSeen(uuid, name, Instant.now(), PlayerRank(uuid, "default", null))
        sessions.put(PlayerSession(uuid, name, UUID.randomUUID(), null, "default", 0, Instant.now()))
    }

    private fun command(
        target: String,
        id: String?,
        command: String,
    ) = mapOf("target" to target, "id" to id, "command" to command)

    private fun transfer(
        groupId: String? = null,
        kick: String? = null,
    ) = mapOf("serverId" to null, "groupId" to groupId, "routingStrategy" to null, "kickMessage" to kick)

    private companion object {
        const val SERVERS = "/api/v1/servers"
        const val PLAYERS = "/api/v1/players"
        const val COMMANDS = "/api/v1/commands"
        const val SEARCH = "/api/v1/search"
        const val GROUPS = "/api/v1/groups"
        const val SUFFIX = 8
        const val SEARCH_LENGTH = 9
        const val OK = 200
        const val ACCEPTED = 202
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
    }
}
