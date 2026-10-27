package io.ogwars.cloud.controller.runtime

import io.ogwars.cloud.controller.config.RuntimeProperties
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Opens a third-party jar for download; the caller verifies its hash. */
fun interface ArtifactFetcher {
    fun open(url: URI): InputStream
}

@Component
class HttpArtifactFetcher(
    private val properties: RuntimeProperties,
) : ArtifactFetcher {
    private val http =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(properties.downloadTimeout)
            .build()

    override fun open(url: URI): InputStream {
        val request =
            HttpRequest
                .newBuilder(url)
                .timeout(properties.downloadTimeout)
                .header("User-Agent", USER_AGENT)
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != OK) {
            response.body().close()
            throw IOException("Downloading $url failed with HTTP ${response.statusCode()}")
        }
        return response.body()
    }

    private companion object {
        const val OK = 200

        // Modrinth asks API clients to identify themselves.
        const val USER_AGENT = "ogcloud-controller (github.com/Jevzo/ogcloud)"
    }
}
