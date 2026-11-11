package io.ogwars.cloud.api.operations

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.KafkaProbe
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.overview.GroupLoad
import io.ogwars.cloud.domain.overview.LoadHistory
import io.ogwars.cloud.domain.overview.LoadSample
import io.ogwars.cloud.domain.scaling.ScalingAction
import io.ogwars.cloud.domain.scaling.ScalingLogEntry
import io.ogwars.cloud.domain.scaling.ScalingReason
import io.ogwars.cloud.domain.server.server
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.LoadHistoryStore
import io.ogwars.cloud.domain.store.ScalingLogStore
import io.ogwars.cloud.domain.store.ServerStateStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.messaging.KafkaTopics
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** NPCs, runtime bundles, logs and the overview (D-0104). */
@ApiTest
class OperationsTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val groups: GroupStore,
    @Autowired private val servers: ServerStateStore,
    @Autowired private val scaling: ScalingLogStore,
    @Autowired private val history: LoadHistoryStore,
    @Value("\${ogcloud.kafka.bootstrap-servers}") kafka: String,
) {
    private val oauth = OAuthHttp(port)
    private val probe = KafkaProbe(kafka)
    private val testUsers = TestUsers(users, passwords)
    private val adminUser = testUsers.create(BuiltInRole.ADMIN.id)
    private val admin = testUsers.token(adminUser)

    @Test
    fun `NPCs are managed per group and every change is announced`() {
        val hub = newGroup()
        val arena = newGroup()

        val created = oauth.call("POST", NPCS, admin, npc(hub, clickTo = arena))
        created.statusCode() shouldBe CREATED
        val id = oauth.json(created)["id"] as String
        oauth.jsonList(oauth.call("GET", "$NPCS?groupId=$hub", admin)).size shouldBe 1

        oauth.call("PUT", "$NPCS/$id", admin, npc(arena, clickTo = hub)).statusCode() shouldBe OK
        probe.received(KafkaTopics.CONFIG_CHANGED) {
            ConfigChanged.parseFrom(it).let { c ->
                c.subjectId == hub &&
                    c.npc.deleted
            }
        } shouldBe
            true
        oauth.jsonList(oauth.call("GET", "$NPCS?groupId=$hub", admin)) shouldBe emptyList<Any>()

        oauth.call("POST", NPCS, admin, npc(hub, clickTo = "no-such-group")).statusCode() shouldBe BAD_REQUEST
        val badTitle = oauth.call("POST", NPCS, admin, npc(hub, clickTo = hub) + ("title" to "{name} wins"))
        badTitle.statusCode() shouldBe BAD_REQUEST
        (
            (
                oauth.json(
                    badTitle,
                )["textErrors"] as Map<*, *>
            )["title"] as List<*>
        ).map { (it as Map<*, *>)["code"] } shouldBe
            listOf("UNKNOWN_PLACEHOLDER")
        oauth.call("DELETE", "$NPCS/$id", admin).statusCode() shouldBe NO_CONTENT
        oauth.call("GET", "$NPCS/$id", admin).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `runtime profiles are listed and a rollout starts a job`() {
        val profiles = oauth.jsonList(oauth.call("GET", RUNTIME, admin)).map { (it as Map<*, *>)["id"] }
        profiles shouldContain "paper-26.3"

        val job = oauth.call("POST", "$RUNTIME/paper-26.3/rollout", admin, mapOf("batchSize" to 3))
        job.statusCode() shouldBe ACCEPTED
        oauth.json(job)["payload"] shouldBe mapOf("profileId" to "paper-26.3", "batchSize" to "3")
        oauth.call("POST", "$RUNTIME/paper-0.1/rollout", admin, mapOf("batchSize" to null)).statusCode() shouldBe
            NOT_FOUND
    }

    @Test
    fun `logs page newest first`() {
        val groupId = newGroup()
        repeat(2) {
            scaling.append(
                ScalingLogEntry(
                    UUID.randomUUID(),
                    Instant.now(),
                    GroupId(groupId),
                    ScalingAction.START,
                    null,
                    ScalingReason.LOAD,
                ),
            )
        }
        oauth.call("POST", NPCS, admin, npc(groupId, clickTo = groupId))

        val firstScaling = oauth.json(oauth.call("GET", "$LOGS/scaling?groupId=$groupId&limit=1", admin))
        val secondScaling =
            oauth.json(
                oauth.call("GET", "$LOGS/scaling?groupId=$groupId&limit=1&cursor=${firstScaling["nextCursor"]}", admin),
            )
        val audit = oauth.json(oauth.call("GET", "$LOGS/audit?limit=5", admin))["items"] as List<*>

        (firstScaling["items"] as List<*>).size shouldBe 1
        secondScaling["nextCursor"] shouldBe null
        audit.map { (it as Map<*, *>)["action"] } shouldContain "POST /api/v1/npcs"
    }

    @Test
    fun `the audit log is filtered by actor, action, target and time`() {
        val since = Instant.now().minusSeconds(1)
        val groupId = newGroup()
        val id = oauth.json(oauth.call("POST", NPCS, admin, npc(groupId, clickTo = groupId)))["id"] as String
        oauth.call("DELETE", "$NPCS/$id", admin)
        val actor = "user:${adminUser.id}"
        val create = URLEncoder.encode("POST /api/v1/npcs", Charsets.UTF_8)
        val items = { query: String ->
            (oauth.json(oauth.call("GET", "$LOGS/audit?$query", admin))["items"] as List<*>).map { it as Map<*, *> }
        }

        val created = items("actor=$actor&action=$create&from=$since")
        val deleted = items("targetType=npcs&targetId=$id")
        val earlier = items("actor=$actor&until=$since")

        created.size shouldBe 1
        deleted.map { it["action"] } shouldBe listOf("DELETE /api/v1/npcs/{id}")
        earlier.map { it["targetId"] } shouldNotContain id
        oauth.call("GET", "$LOGS/audit?actor=nobody", admin).statusCode() shouldBe BAD_REQUEST
        oauth.call("GET", "$LOGS/audit?from=$since&until=$since", admin).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `the overview counts players, servers and capacity per group`() {
        val groupId = newGroup()
        servers.create(server(group = groupId, id = UUID.randomUUID(), players = PLAYERS))

        val overview = oauth.json(oauth.call("GET", OVERVIEW, admin))

        val row = (overview["groups"] as List<*>).map { it as Map<*, *> }.single { it["id"] == groupId }
        row["running"] shouldBe 1
        row["players"] shouldBe PLAYERS
        row["capacity"] shouldBe
            groups
                .find(GroupId(groupId))
                ?.value
                ?.scaling
                ?.playersPerServer
        (overview["serversByState"] as Map<*, *>).keys shouldContain "RUNNING"
    }

    @Test
    fun `the history serves the last day of samples for the network or one group`() {
        val groupId = newGroup()
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val load = GroupLoad(running = 2, pending = 0, players = PLAYERS)
        history.record(LoadSample(now.minus(Duration.ofHours(25)), 1, emptyMap()), Duration.ofDays(2))
        history.record(LoadSample(now.minusSeconds(120), 4, emptyMap()), LoadHistory.KEPT)
        history.record(LoadSample(now.minusSeconds(60), PLAYERS + 1, mapOf(GroupId(groupId) to load)), LoadHistory.KEPT)

        val network = oauth.json(oauth.call("GET", "$OVERVIEW/history", admin))
        val group = oauth.json(oauth.call("GET", "$OVERVIEW/history?groupId=$groupId", admin))

        network["intervalSeconds"] shouldBe LoadHistory.INTERVAL.toSeconds().toInt()
        val networkPoints = (network["points"] as List<*>).map { it as Map<*, *> }
        networkPoints.none { it["at"] == now.minus(Duration.ofHours(25)).toString() } shouldBe true
        networkPoints.single { it["at"] == now.minusSeconds(60).toString() } shouldBe
            mapOf("at" to now.minusSeconds(60).toString(), "players" to PLAYERS + 1, "running" to 2)
        group["points"] shouldBe
            listOf(mapOf("at" to now.minusSeconds(60).toString(), "players" to PLAYERS, "running" to 2))
        oauth.call("GET", "$OVERVIEW/history?groupId=missing-group", admin).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `moderators read logs but neither runtimes nor NPC changes`() {
        val moderator = testUsers.token(testUsers.create(BuiltInRole.MODERATOR.id))

        oauth.call("GET", "$LOGS/audit", moderator).statusCode() shouldBe OK
        oauth.call("GET", RUNTIME, moderator).statusCode() shouldBe FORBIDDEN
        oauth.call("POST", NPCS, moderator, npc(newGroup(), clickTo = newGroup())).statusCode() shouldBe FORBIDDEN
    }

    private fun newGroup(): String {
        val id = "ops-${UUID.randomUUID().toString().take(SUFFIX)}"
        groups.create(group(id = id))
        return id
    }

    private fun npc(
        groupId: String,
        clickTo: String,
    ) = mapOf(
        "groupId" to groupId,
        "location" to mapOf("world" to "world", "x" to 0.5, "y" to 64.0, "z" to 0.5, "yaw" to 90.0, "pitch" to 0.0),
        "title" to "<green>Play",
        "subtitle" to "{players} playing",
        "skin" to null,
        "lookAtPlayers" to true,
        "leftClick" to null,
        "rightClick" to mapOf("groupId" to clickTo, "routingStrategy" to null),
    )

    private companion object {
        const val NPCS = "/api/v1/npcs"
        const val RUNTIME = "/api/v1/runtime"
        const val LOGS = "/api/v1/logs"
        const val OVERVIEW = "/api/v1/overview"
        const val SUFFIX = 8
        const val PLAYERS = 7
        const val OK = 200
        const val CREATED = 201
        const val ACCEPTED = 202
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
    }
}
