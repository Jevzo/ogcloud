package io.ogwars.cloud.api.text

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.audit.AuditFilter
import io.ogwars.cloud.domain.store.AuditLogStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.ogwars.cloud.text.TextField
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder

/** The dashboard's previews of in-game texts (D-0202, D-0205), which any signed-in caller may ask for. */
@ApiTest
class TextPreviewTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
    @Autowired private val audit: AuditLogStore,
) {
    private val oauth = OAuthHttp(port)
    private val viewer = TestUsers(users, passwords).let { it.token(it.create(BuiltInRole.VIEWER.id)) }

    @Test
    fun `a text shows as 1_16+ clients see it and as older ones do, unaudited`() {
        val preview = preview("<gradient:#ff0000:#0000ff>VIP</gradient> <bold>!", "CHAT_PREFIX")

        preview["errors"] shouldBe emptyList<Any>()
        colours(modern(preview)) shouldBe
            listOf("V" to "#ff0000", "I" to "#800080", "P" to "#0000ff", " " to null, "!" to null)
        colours(legacy(preview)) shouldBe
            listOf("V" to "#aa0000", "I" to "#aa00aa", "P" to "#0000aa", " " to null, "!" to null)
        (modern(preview).last() as Map<*, *>)["bold"] shouldBe true
        audit.list(AuditFilter.NONE, before = null, limit = AUDIT_PAGE).map { it.action } shouldNotContain
            "POST $PREVIEW"
    }

    @Test
    fun `placeholders show samples, and a tab prefix keeps one colour and what fits on 1_8`() {
        colours(modern(preview("<gold>{name}", "NAME_FORMAT"))) shouldBe listOf("Steve" to "#ffaa00")

        val tab = preview("<gradient:#ff5555:#5555ff>[Administrator]</gradient>", "TAB_PREFIX")
        colours(legacy(tab)) shouldBe listOf("[Administrat" to "#ff5555")
        colours(modern(tab)).size shouldBe "[Administrator]".length
    }

    @Test
    fun `a preview names the placeholders its field offers, valid or not`() {
        preview("<gold>{name}", "NAME_FORMAT")["placeholders"] shouldBe listOf("name")
        preview("<frog>", "NAME_FORMAT")["placeholders"] shouldBe listOf("name")
        preview("hi", "MOTD")["placeholders"] shouldBe emptyList<String>()
    }

    @Test
    fun `version names show as every client sees them, in legacy colours`() {
        val version = preview("<gradient:#ff5555:#5555ff>OgW</gradient>", "VERSION_NAME")

        modern(version) shouldBe legacy(version)
        colours(legacy(version)) shouldBe listOf("O" to "#ff5555", "g" to "#ff55ff", "W" to "#5555ff")
    }

    @Test
    fun `an invalid text answers its coded errors instead`() {
        val preview = preview("hi {player} <frog>", "MOTD")

        preview["errors"] shouldBe
            listOf(
                mapOf("code" to "UNKNOWN_TAG", "offset" to 12, "length" to 6, "reason" to "unknown tag <frog>"),
            )
        preview["rendering"] shouldBe null
        preview("{player}", "KICK_MESSAGE")["errors"] shouldBe
            listOf(
                mapOf(
                    "code" to "UNKNOWN_PLACEHOLDER",
                    "offset" to 0,
                    "length" to 8,
                    "reason" to "{player} is no placeholder here; this text offers none",
                ),
            )
        (
            preview(
                "x".repeat(TextField.MAX_LENGTH + 1),
                "MOTD",
            )["errors"] as List<*>
        ).map { (it as Map<*, *>)["code"] } shouldBe
            listOf("TOO_LONG")
    }

    @Test
    fun `only signed-in callers may preview, and only fields that exist`() {
        oauth.call("POST", PREVIEW, null, mapOf("text" to "x", "field" to "MOTD")).statusCode() shouldBe UNAUTHORIZED
        oauth.call("POST", PREVIEW, viewer, mapOf("text" to "x", "field" to "FROG")).statusCode() shouldBe BAD_REQUEST
    }

    private fun preview(
        text: String,
        field: String,
    ): Map<*, *> {
        val response = oauth.call("POST", PREVIEW, viewer, mapOf("text" to text, "field" to field))
        response.statusCode() shouldBe OK
        return oauth.json(response)
    }

    private fun modern(preview: Map<*, *>) = (preview["rendering"] as Map<*, *>)["modern"] as List<*>

    private fun legacy(preview: Map<*, *>) = (preview["rendering"] as Map<*, *>)["legacy"] as List<*>

    /** Each span's text with the colour it shows in. */
    private fun colours(spans: List<*>) = spans.map { (it as Map<*, *>)["text"] to it["color"] }

    private companion object {
        const val PREVIEW = "/api/v1/text/preview"
        const val OK = 200
        const val BAD_REQUEST = 400
        const val UNAUTHORIZED = 401
        const val AUDIT_PAGE = 100
    }
}
