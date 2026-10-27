package io.ogwars.cloud.api.groups

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.KafkaProbe
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.contracts.events.v1.ConfigChanged
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
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
import java.net.http.HttpResponse
import java.util.UUID

@ApiTest
class GroupAdministrationTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Value("\${ogcloud.kafka.bootstrap-servers}") private val kafka: String,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val admin = testUsers.token(testUsers.create(BuiltInRole.ADMIN.id))

    @Test
    fun `a created group is announced to the controller`() {
        val id = groupId()

        oauth.call("POST", GROUPS, admin, create(id)).statusCode() shouldBe CREATED

        oauth.json(oauth.call("GET", "$GROUPS/$id", admin))["id"] shouldBe id
        announced(id) shouldBe true
    }

    @Test
    fun `updates need the version read and keep the type`() {
        val id = groupId()
        val read = version(oauth.call("POST", GROUPS, admin, create(id)))

        val updated = oauth.call("PUT", "$GROUPS/$id", admin, update(version = read, maintenance = true))
        updated.statusCode() shouldBe OK
        oauth.call("PUT", "$GROUPS/$id", admin, update(version = read)).statusCode() shouldBe CONFLICT
        val proxyProfile = update(version = version(updated), profileId = "velocity-4.3.0")
        oauth.call("PUT", "$GROUPS/$id", admin, proxyProfile).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `invalid groups are refused`() {
        oauth.call("POST", GROUPS, admin, create(groupId(), profileId = "paper-0.1")).statusCode() shouldBe BAD_REQUEST
        oauth.call("POST", GROUPS, admin, create(groupId(), type = "PROXY")).statusCode() shouldBe BAD_REQUEST
        oauth.call("POST", GROUPS, admin, create("Not A Label")).statusCode() shouldBe BAD_REQUEST
        val foreignImage = create(groupId(), image = "docker.io/evil/paper:latest")
        oauth.call("POST", GROUPS, admin, foreignImage).statusCode() shouldBe BAD_REQUEST
    }

    @Test
    fun `a deleted group can be restored, and its delete job is visible`() {
        val id = groupId()
        oauth.call("POST", GROUPS, admin, create(id))

        val job = oauth.call("DELETE", "$GROUPS/$id", admin)

        job.statusCode() shouldBe ACCEPTED
        oauth.json(job)["kind"] shouldBe "group.delete"
        oauth.call("GET", "$GROUPS/$id", admin).statusCode() shouldBe NOT_FOUND
        deletedIds() shouldContain id
        oauth.call("GET", "/api/v1/jobs/${oauth.json(job)["id"]}", admin).statusCode() shouldBe OK

        oauth.call("POST", "$GROUPS/$id/restore", admin).statusCode() shouldBe OK
        deletedIds() shouldNotContain id
        oauth.call("POST", "$GROUPS/$id/restore", admin).statusCode() shouldBe NOT_FOUND
    }

    @Test
    fun `restarts need maintenance and a positive batch size`() {
        val id = groupId()
        val read = version(oauth.call("POST", GROUPS, admin, create(id)))

        oauth.call("POST", "$GROUPS/$id/restart", admin, mapOf("batchSize" to 2)).statusCode() shouldBe CONFLICT
        oauth.call("PUT", "$GROUPS/$id", admin, update(version = read, maintenance = true)).statusCode() shouldBe OK
        oauth.call("POST", "$GROUPS/$id/restart", admin, mapOf("batchSize" to 0)).statusCode() shouldBe BAD_REQUEST
        val job = oauth.json(oauth.call("POST", "$GROUPS/$id/restart", admin, mapOf("batchSize" to 2)))

        job["payload"] shouldBe mapOf("groupId" to id, "batchSize" to "2")
    }

    @Test
    fun `viewers read groups and their jobs but change nothing`() {
        val viewer = testUsers.token(testUsers.create(BuiltInRole.VIEWER.id))
        val id = groupId()
        oauth.call("POST", GROUPS, admin, create(id))
        val job = oauth.json(oauth.call("DELETE", "$GROUPS/$id", admin))

        oauth.call("GET", GROUPS, viewer).statusCode() shouldBe OK
        oauth.call("POST", GROUPS, viewer, create(groupId())).statusCode() shouldBe FORBIDDEN
        oauth.call("GET", "/api/v1/jobs/${job["id"]}", viewer).statusCode() shouldBe OK
        val networkOnly = testUsers.token(testUsers.create(BuiltInRole.ADMIN.id), listOf(ApiPermission.NETWORK_READ))
        oauth.call("GET", "/api/v1/jobs/${job["id"]}", networkOnly).statusCode() shouldBe FORBIDDEN
        val listed = oauth.json(oauth.call("GET", "/api/v1/jobs", networkOnly))["items"] as List<*>
        listed.none { (it as Map<*, *>)["id"] == job["id"] } shouldBe true
    }

    private fun deletedIds() =
        oauth
            .jsonList(
                oauth.call("GET", "$GROUPS/deleted", admin),
            ).map { ((it as Map<*, *>)["group"] as Map<*, *>)["id"] }

    private fun announced(groupId: String): Boolean =
        KafkaProbe(kafka).received(KafkaTopics.CONFIG_CHANGED) { ConfigChanged.parseFrom(it).group.id == groupId }

    private fun version(response: HttpResponse<String>) = (oauth.json(response)["version"] as Number).toLong()

    private fun groupId() = "g-${UUID.randomUUID().toString().take(SUFFIX)}"

    private fun create(
        id: String,
        type: String = "DYNAMIC",
        profileId: String = PAPER,
        image: String? = null,
    ) = mapOf("id" to id, "type" to type, "settings" to settings(profileId = profileId, image = image))

    private fun update(
        version: Long,
        maintenance: Boolean = false,
        profileId: String = PAPER,
    ) = mapOf("settings" to settings(maintenance = maintenance, profileId = profileId), "version" to version)

    private fun settings(
        maintenance: Boolean = false,
        profileId: String = PAPER,
        image: String? = null,
    ) = mapOf(
        "profileId" to profileId,
        "scaling" to
            mapOf(
                "minOnline" to 1,
                "maxInstances" to 5,
                "playersPerServer" to 16,
                "scaleUpThreshold" to 0.8,
                "scaleDownThreshold" to 0.2,
                "cooldownSeconds" to 60,
            ),
        "drainTimeoutSeconds" to 120,
        "maintenance" to maintenance,
        "resources" to
            mapOf("memoryRequest" to "1Gi", "memoryLimit" to "2Gi", "cpuRequest" to "500m", "cpuLimit" to null),
        "jvmFlags" to "",
        "image" to image?.let { mapOf("image" to it, "pullSecretRef" to null) },
        "storageSize" to null,
    )

    private companion object {
        const val GROUPS = "/api/v1/groups"
        const val PAPER = "paper-26.3"
        const val SUFFIX = 8
        const val OK = 200
        const val CREATED = 201
        const val ACCEPTED = 202
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
    }
}
