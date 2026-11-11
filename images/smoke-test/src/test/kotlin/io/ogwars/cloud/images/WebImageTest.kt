package io.ogwars.cloud.images

import com.github.dockerjava.api.model.Capability
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * The web image (D-0214) serves the dashboard as the chart runs it: read-only, every capability dropped. Every route
 * is index.html with a fresh CSP nonce (D-0219), and the hashed assets are cached for good.
 */
class WebImageTest {
    private val http = HttpClient.newHttpClient()

    @Test
    fun `routes get index html with a fresh nonce that the CSP names`() {
        web().use { web ->
            val first = get(web, "/servers/0195b1d2")
            val second = get(web, "/")

            first.statusCode() shouldBe OK
            val nonce = nonceOf(first)
            first.body() shouldContain """<meta name="csp-nonce" content="$nonce" />"""
            nonceOf(second) shouldNotBe nonce
            val policy = first.headers().firstValue(CSP).orElse("")
            policy shouldContain "script-src 'self';"
            policy shouldContain "frame-ancestors 'none'"
            first.headers().firstValue("Cache-Control").orElse("") shouldBe "no-store"
            first.headers().firstValue("X-Content-Type-Options").orElse("") shouldBe "nosniff"
        }
    }

    @Test
    fun `hashed assets are cached for a year`() {
        web().use { web ->
            val script =
                SCRIPT.find(get(web, "/").body())?.groupValues?.get(1) ?: error("index.html loads no script")
            val asset = get(web, script)

            asset.statusCode() shouldBe OK
            asset.headers().firstValue("Cache-Control").orElse("") shouldContain "immutable"
        }
    }

    private fun web(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("${property("registry")}/web:${property("version")}"))
            .withExposedPorts(PORT)
            .withTmpFs(mapOf("/tmp" to "rw"))
            .withCreateContainerCmdModifier { command ->
                command.hostConfig
                    ?.withReadonlyRootfs(true)
                    ?.withCapDrop(Capability.ALL)
                    ?.withSecurityOpts(listOf("no-new-privileges"))
            }.waitingFor(Wait.forHttp("/healthz"))
            .also { it.start() }

    private fun get(
        web: GenericContainer<*>,
        path: String,
    ): HttpResponse<String> =
        http.send(
            HttpRequest.newBuilder(URI("http://${web.host}:${web.getMappedPort(PORT)}$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun nonceOf(response: HttpResponse<String>): String =
        NONCE.find(response.headers().firstValue(CSP).orElse(""))?.groupValues?.get(1)
            ?: error("The CSP names no nonce")

    private companion object {
        const val PORT = 8080
        const val OK = 200
        const val CSP = "Content-Security-Policy"
        val NONCE = Regex("'nonce-([^']+)'")
        val SCRIPT = Regex("""<script type="module" crossorigin src="([^"]+)">""")
    }
}
