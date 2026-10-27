package io.ogwars.cloud.api.templates

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.group.GroupType
import io.ogwars.cloud.domain.group.group
import io.ogwars.cloud.domain.store.GroupStore
import io.ogwars.cloud.domain.store.ObjectStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.template.TemplateKeys
import io.ogwars.cloud.domain.user.BuiltInRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

@ApiTest
class TemplatesTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val groups: GroupStore,
    @Autowired private val objects: ObjectStore,
) {
    private val oauth = OAuthHttp(port)
    private val testUsers = TestUsers(users, passwords)
    private val admin = testUsers.token(testUsers.create(BuiltInRole.ADMIN.id))

    @Test
    fun `an upload becomes a version that round-trips and is promoted on request`() {
        val groupId = group(GroupType.DYNAMIC)
        val archive = archive("lobby world")

        val uploaded = oauth.upload(templates(groupId), admin, archive, GZIP)

        uploaded.statusCode() shouldBe CREATED
        val version = oauth.json(uploaded)
        version["active"] shouldBe false
        version["sha256"] shouldBe HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive))
        oauth.download("${templates(groupId)}/${version["id"]}/content", admin).body().toList() shouldBe
            archive.toList()

        val promoted = oauth.call("PUT", "${templates(groupId)}/active", admin, mapOf("versionId" to version["id"]))
        oauth.json(promoted)["active"] shouldBe true
        groups
            .find(GroupId(groupId))
            ?.value
            ?.activeTemplateVersionId
            .toString() shouldBe version["id"]
    }

    @Test
    fun `a STATIC group starts from every new version`() {
        val groupId = group(GroupType.STATIC)

        oauth.json(oauth.upload(templates(groupId), admin, archive("smp world"), GZIP))["active"] shouldBe true
    }

    @Test
    fun `only gzip archives within the size limit are taken`() {
        val groupId = group(GroupType.DYNAMIC)

        oauth.upload(templates(groupId), admin, "not an archive".toByteArray(), GZIP).statusCode() shouldBe BAD_REQUEST
        oauth.upload(templates(groupId), admin, ByteArray(0), GZIP).statusCode() shouldBe BAD_REQUEST
        oauth.upload(templates(groupId), admin, archive(Random(1).nextBytes(OVERSIZED)), GZIP).statusCode() shouldBe
            CONTENT_TOO_LARGE
        // Without a Content-Length only the counting stream can tell, and the partial upload must not stay behind.
        oauth
            .upload(
                templates(groupId),
                admin,
                archive(Random(2).nextBytes(OVERSIZED)),
                GZIP,
                chunked = true,
            ).statusCode() shouldBe
            CONTENT_TOO_LARGE
        oauth.jsonList(oauth.call("GET", templates(groupId), admin)) shouldBe emptyList<Any>()
        objects.list("${TemplateKeys.PREFIX}$groupId/") shouldBe emptyList()
    }

    @Test
    fun `the active version stays, others can be deleted`() {
        val groupId = group(GroupType.DYNAMIC)
        val first = oauth.json(oauth.upload(templates(groupId), admin, archive("first"), GZIP))["id"]
        val second = oauth.json(oauth.upload(templates(groupId), admin, archive("second"), GZIP))["id"]
        oauth.call("PUT", "${templates(groupId)}/active", admin, mapOf("versionId" to second))

        oauth.call("DELETE", "${templates(groupId)}/$second", admin).statusCode() shouldBe CONFLICT
        oauth.call("DELETE", "${templates(groupId)}/$first", admin).statusCode() shouldBe NO_CONTENT
        oauth.download("${templates(groupId)}/$first/content", admin).statusCode() shouldBe NOT_FOUND
        oauth
            .call(
                "PUT",
                "${templates(group(GroupType.DYNAMIC))}/active",
                admin,
                mapOf("versionId" to second),
            ).statusCode() shouldBe
            NOT_FOUND
    }

    @Test
    fun `viewers download but do not upload`() {
        val viewer = testUsers.token(testUsers.create(BuiltInRole.VIEWER.id))
        val groupId = group(GroupType.DYNAMIC)

        oauth.call("GET", templates(groupId), viewer).statusCode() shouldBe OK
        oauth.upload(templates(groupId), viewer, archive("x"), GZIP).statusCode() shouldBe FORBIDDEN
    }

    private fun group(type: GroupType): String {
        val id = "tpl-${UUID.randomUUID().toString().take(SUFFIX)}"
        groups.create(group(type, id = id))
        return id
    }

    private fun templates(groupId: String) = "/api/v1/groups/$groupId/templates"

    private fun archive(content: String) = archive(content.toByteArray())

    private fun archive(content: ByteArray): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                GZIPOutputStream(out).use {
                    it.write(content)
                }
            }.toByteArray()

    private companion object {
        const val GZIP = "application/gzip"
        const val SUFFIX = 8

        /** Random bytes barely compress, so the archive stays above the test limit of 1 MB. */
        const val OVERSIZED = 2 * 1024 * 1024
        const val OK = 200
        const val CREATED = 201
        const val NO_CONTENT = 204
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
        const val CONTENT_TOO_LARGE = 413
    }
}
