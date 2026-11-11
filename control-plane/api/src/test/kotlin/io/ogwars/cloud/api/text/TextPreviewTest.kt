package io.ogwars.cloud.api.text

import io.ogwars.cloud.apitest.ApiTest
import io.ogwars.cloud.apitest.OAuthHttp
import io.ogwars.cloud.apitest.TestUsers
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.BuiltInRole
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder

/** The dashboard's previews of in-game texts (D-0202), which any signed-in user may ask for. */
@ApiTest
class TextPreviewTest(
    @LocalServerPort port: Int,
    @Autowired users: WebUserStore,
    @Autowired passwords: PasswordEncoder,
) {
    private val oauth = OAuthHttp(port)
    private val viewer = TestUsers(users, passwords).let { it.token(it.create(BuiltInRole.VIEWER.id)) }

    @Test
    fun `a text shows as 1_16+ clients see it and as older ones do`() {
        val preview = preview("<gradient:#ff0000:#0000ff>VIP</gradient> <bold>!", "CHAT_PREFIX")

        preview["errors"] shouldBe emptyList<Any>()
        colours(preview["modern"]) shouldBe
            listOf("V" to "#ff0000", "I" to "#800080", "P" to "#0000ff", " " to null, "!" to null)
        colours(preview["legacy"]) shouldBe
            listOf("V" to "#aa0000", "I" to "#aa00aa", "P" to "#0000aa", " " to null, "!" to null)
        ((preview["modern"] as List<*>).last() as Map<*, *>)["bold"] shouldBe true
    }

    @Test
    fun `placeholders show samples, and a tab prefix keeps one colour on 1_8`() {
        val name = preview("<gold>{name}", "NAME_FORMAT")
        colours(name["modern"]) shouldBe listOf("Steve" to "#ffaa00")

        val tab = preview("<gradient:#ff5555:#5555ff>VIP</gradient>", "TAB_PREFIX")
        colours(tab["legacy"]) shouldBe listOf("VIP" to "#ff5555")
    }

    @Test
    fun `an invalid text answers its errors instead`() {
        val preview = preview("hi {player} <frog>", "MOTD")

        preview["errors"] shouldBe
            listOf(
                mapOf("offset" to 12, "reason" to "unknown tag <frog>"),
            )
        preview["modern"] shouldBe emptyList<Any>()
        preview("{player}", "MOTD")["errors"] shouldBe
            listOf(mapOf("offset" to 0, "reason" to "{player} is no placeholder here; this text offers none"))
    }

    @Test
    fun `only signed-in callers may preview`() {
        oauth.call("POST", PREVIEW, null, mapOf("text" to "x", "field" to "MOTD")).statusCode() shouldBe UNAUTHORIZED
    }

    private fun preview(
        text: String,
        field: String,
    ): Map<*, *> {
        val response = oauth.call("POST", PREVIEW, viewer, mapOf("text" to text, "field" to field))
        response.statusCode() shouldBe OK
        return oauth.json(response)
    }

    /** Each span's text with the colour it shows in. */
    private fun colours(spans: Any?) = (spans as List<*>).map { (it as Map<*, *>)["text"] to it["color"] }

    private companion object {
        const val PREVIEW = "/api/v1/text/preview"
        const val OK = 200
        const val UNAUTHORIZED = 401
    }
}
