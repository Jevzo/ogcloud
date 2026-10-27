package io.ogwars.cloud.controller.runtime

import io.ogwars.cloud.controller.config.RuntimeProperties
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

/** Against a local HTTP server: no network beyond the test JVM. */
class HttpArtifactFetcherTest {
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
            createContext("/ViaVersion.jar") { exchange ->
                exchange.sendResponseHeaders(OK, JAR.size.toLong())
                exchange.responseBody.use { it.write(JAR) }
            }
            start()
        }
    private val fetcher = HttpArtifactFetcher(RuntimeProperties())

    @AfterEach
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `downloads a jar and fails on an error status`() {
        fetcher.open(url("/ViaVersion.jar")).use { it.readAllBytes() } shouldBe JAR
        shouldThrow<IOException> { fetcher.open(url("/missing.jar")) }
    }

    private fun url(path: String) = URI("http://localhost:${server.address.port}$path")

    private companion object {
        const val OK = 200
        val JAR = "jar-bytes".toByteArray()
    }
}
