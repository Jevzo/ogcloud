package io.ogwars.cloud.paper.api

import com.google.gson.Gson
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

data class ApiServerRequestResponse(
    val serverId: String,
    val group: String
)

data class ApiAuthTokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String
)

class ApiClient(
    baseUrl: String,
    private val authEmail: String,
    private val authPassword: String,
    private val logger: Logger
) {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .build()

    private val gson = Gson()
    private val base = baseUrl.trimEnd('/')
    private val authLock = Any()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var accessTokenExpiresAt: Instant = Instant.EPOCH

    @Volatile
    private var refreshToken: String? = null

    @Volatile
    private var refreshTokenExpiresAt: Instant = Instant.EPOCH

    fun requestServer(group: String): CompletableFuture<ApiServerRequestResponse> {
        return postWithResponse(
            "/api/v1/servers/request",
            mapOf("group" to group),
            ApiServerRequestResponse::class.java
        )
    }

    fun forceTemplatePush(id: String): CompletableFuture<Void> {
        return post("/api/v1/servers/$id/template/push", null)
    }

    fun transferPlayer(uuid: String, target: String): CompletableFuture<Void> {
        return post("/api/v1/players/$uuid/transfer", mapOf("target" to target))
    }

    private fun post(path: String, body: Any?): CompletableFuture<Void> {
        return sendAuthorizedPost(path, body).thenApply { null }
    }

    private fun <T> postWithResponse(path: String, body: Any?, type: Class<T>): CompletableFuture<T> {
        return sendAuthorizedPost(path, body).thenApply { response -> gson.fromJson(response.body(), type) }
    }

    private fun ensureAccessToken(): String {
        synchronized(authLock) {
            val now = Instant.now()
            val currentAccessToken = accessToken

            if (currentAccessToken != null && accessTokenExpiresAt.isAfter(now.plus(AUTH_EXPIRY_SKEW))) {
                return currentAccessToken
            }

            val currentRefreshToken = refreshToken
            if (currentRefreshToken != null && refreshTokenExpiresAt.isAfter(now.plus(AUTH_EXPIRY_SKEW))) {
                try {
                    refreshSync(currentRefreshToken)
                    return accessToken ?: error("Refresh succeeded without access token")
                } catch (ex: Exception) {
                    logger.warning("API token refresh failed, falling back to login: ${ex.message}")
                }
            }

            loginSync()

            return accessToken ?: error("Login succeeded without access token")
        }
    }

    private fun loginSync() {
        val response = sendAuthRequest("/api/v1/auth/login", mapOf("email" to authEmail, "password" to authPassword))
        setTokens(response)

        logger.info("Authenticated Paper API client with web account $authEmail")
    }

    private fun refreshSync(currentRefreshToken: String) {
        val response = sendAuthRequest("/api/v1/auth/refresh", mapOf("refreshToken" to currentRefreshToken))
        setTokens(response)
    }

    private fun sendAuthRequest(path: String, body: Any): ApiAuthTokenResponse {
        val response = httpClient.send(
            buildPostRequest(path, body),
            HttpResponse.BodyHandlers.ofString()
        ).validated()
        return gson.fromJson(response.body(), ApiAuthTokenResponse::class.java)
    }

    private fun setTokens(response: ApiAuthTokenResponse) {
        accessToken = response.accessToken
        accessTokenExpiresAt = Instant.parse(response.accessTokenExpiresAt)
        refreshToken = response.refreshToken
        refreshTokenExpiresAt = Instant.parse(response.refreshTokenExpiresAt)
    }

    private fun sendAuthorizedPost(path: String, body: Any?): CompletableFuture<HttpResponse<String>> {
        return httpClient.sendAsync(
            buildPostRequest(path, body, ensureAccessToken()),
            HttpResponse.BodyHandlers.ofString()
        ).thenApply { response -> response.validated() }
    }

    private fun buildPostRequest(path: String, body: Any?, bearerToken: String? = null): HttpRequest {
        return HttpRequest.newBuilder()
            .uri(URI.create("$base$path"))
            .timeout(REQUEST_TIMEOUT)
            .apply {
                if (bearerToken != null) {
                    header("Authorization", "Bearer $bearerToken")
                }
            }
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(serializeBody(body)))
            .build()
    }

    private fun serializeBody(body: Any?): String = gson.toJson(body ?: emptyMap<String, Any>())

    private fun HttpResponse<String>.validated(): HttpResponse<String> {
        if (statusCode() !in 200..299) {
            throw ApiException(statusCode(), body())
        }
        return this
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val AUTH_EXPIRY_SKEW: Duration = Duration.ofSeconds(30)
    }
}

class ApiException(val statusCode: Int, val responseBody: String) :
    RuntimeException("API returned $statusCode: $responseBody")
