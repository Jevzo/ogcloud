package io.ogwars.cloud.images

import io.ogwars.cloud.domain.runtime.RuntimePlugin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Bundle plugins as the controller fetches them (D-0079): from the catalog's url, checked against its SHA-512. */
internal object BundlePlugins {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    fun download(plugin: RuntimePlugin): Path {
        val target =
            Path
                .of(
                    property("cache"),
                ).resolve("${plugin.id}-${plugin.sha512.take(HASH_PREFIX)}.jar")
        if (Files.exists(target) && sha512(target) == plugin.sha512) return target
        Files.createDirectories(target.parent)
        val response =
            client.send(
                HttpRequest.newBuilder(URI.create(plugin.url)).build(),
                HttpResponse.BodyHandlers.ofFile(target),
            )
        check(response.statusCode() == HTTP_OK) { "Downloading ${plugin.url} answered HTTP ${response.statusCode()}" }
        check(sha512(target) == plugin.sha512) { "${plugin.url} does not match the catalog's SHA-512" }
        return target
    }

    private fun sha512(file: Path): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(file)))

    private const val HASH_PREFIX = 12
    private const val HTTP_OK = 200
}
