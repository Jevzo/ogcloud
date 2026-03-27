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
    val group: String,
)

data class ApiAuthTokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
)

data class ApiNetworkGeneralSettingsResponse(
    val permissionSystemEnabled: Boolean = true,
    val tablistEnabled: Boolean = true,
)

data class ApiNetworkSettingsResponse(
    val general: ApiNetworkGeneralSettingsResponse = ApiNetworkGeneralSettingsResponse(),
)

class ApiClient(
    baseUrl: String,
    private val authEmail: String,
    private val authPassword: String,
    private val logger: Logger,
) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()

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

    fun requestServer(group: String): CompletableFuture<ApiServerRequestResponse> =
        postWithResponse(
            "/api/v1/servers/request",
            mapOf("group" to group),
            ApiServerRequestResponse::class.java,
        )

    fun forceTemplatePush(id: String): CompletableFuture<Void> = post("/api/v1/servers/$id/template/push", null)

    fun transferPlayer(
        uuid: String,
        target: String,
    ): CompletableFuture<Void> = post("/api/v1/players/$uuid/transfer", mapOf("target" to target))

    fun listNpcs(group: String? = null): CompletableFuture<List<ApiNpcResponse>> {
        val path =
            if (group != null) {
                "/api/v1/npcs?group=$group"
            } else {
                "/api/v1/npcs"
            }

        return get(path, Array<ApiNpcResponse>::class.java).thenApply { it.toList() }
    }

    fun getNpc(id: String): CompletableFuture<ApiNpcResponse> = get("/api/v1/npcs/$id", ApiNpcResponse::class.java)

    fun createNpc(request: ApiCreateNpcRequest): CompletableFuture<ApiNpcResponse> =
        postWithResponse("/api/v1/npcs", request, ApiNpcResponse::class.java)

    fun updateNpc(
        id: String,
        request: ApiUpdateNpcRequest,
    ): CompletableFuture<ApiNpcResponse> = putWithResponse("/api/v1/npcs/$id", request, ApiNpcResponse::class.java)

    fun deleteNpc(id: String): CompletableFuture<Void> = delete("/api/v1/npcs/$id")

    fun getNetworkSettingsSync(): ApiNetworkSettingsResponse {
        val response =
            httpClient
                .send(
                    buildGetRequest("/api/v1/network", ensureAccessToken()),
                    HttpResponse.BodyHandlers.ofString(),
                ).validated()

        return gson.fromJson(response.body(), ApiNetworkSettingsResponse::class.java)
    }

    private fun post(
        path: String,
        body: Any?,
    ): CompletableFuture<Void> = sendAuthorizedPost(path, body).thenApply { null }

    private fun <T> postWithResponse(
        path: String,
        body: Any?,
        type: Class<T>,
    ): CompletableFuture<T> =
        sendAuthorizedPost(path, body).thenApply { response -> gson.fromJson(response.body(), type) }

    private fun <T> putWithResponse(
        path: String,
        body: Any?,
        type: Class<T>,
    ): CompletableFuture<T> =
        sendAuthorized(
            buildJsonRequest(path, body, HttpMethod.PUT, ensureAccessToken()),
        ).thenApply { response -> gson.fromJson(response.body(), type) }

    private fun <T> get(
        path: String,
        type: Class<T>,
    ): CompletableFuture<T> =
        httpClient
            .sendAsync(
                buildGetRequest(path, ensureAccessToken()),
                HttpResponse.BodyHandlers.ofString(),
            ).thenApply { response -> response.validated() }
            .thenApply { response -> gson.fromJson(response.body(), type) }

    private fun delete(path: String): CompletableFuture<Void> =
        sendAuthorized(buildDeleteRequest(path, ensureAccessToken())).thenApply { null }

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

    private fun sendAuthRequest(
        path: String,
        body: Any,
    ): ApiAuthTokenResponse {
        val response =
            httpClient
                .send(
                    buildPostRequest(path, body),
                    HttpResponse.BodyHandlers.ofString(),
                ).validated()
        return gson.fromJson(response.body(), ApiAuthTokenResponse::class.java)
    }

    private fun setTokens(response: ApiAuthTokenResponse) {
        accessToken = response.accessToken
        accessTokenExpiresAt = Instant.parse(response.accessTokenExpiresAt)
        refreshToken = response.refreshToken
        refreshTokenExpiresAt = Instant.parse(response.refreshTokenExpiresAt)
    }

    private fun sendAuthorizedPost(
        path: String,
        body: Any?,
    ): CompletableFuture<HttpResponse<String>> = sendAuthorized(buildPostRequest(path, body, ensureAccessToken()))

    private fun sendAuthorized(request: HttpRequest): CompletableFuture<HttpResponse<String>> =
        httpClient
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> response.validated() }

    private fun buildPostRequest(
        path: String,
        body: Any?,
        bearerToken: String? = null,
    ): HttpRequest = buildJsonRequest(path, body, HttpMethod.POST, bearerToken)

    private fun buildJsonRequest(
        path: String,
        body: Any?,
        method: HttpMethod,
        bearerToken: String? = null,
    ): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(URI.create("$base$path"))
            .timeout(REQUEST_TIMEOUT)
            .apply {
                if (bearerToken != null) {
                    header("Authorization", "Bearer $bearerToken")
                }
            }.header("Content-Type", "application/json")
            .method(method.name, HttpRequest.BodyPublishers.ofString(serializeBody(body)))
            .build()

    private fun buildGetRequest(
        path: String,
        bearerToken: String,
    ): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(URI.create("$base$path"))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer $bearerToken")
            .header("Content-Type", "application/json")
            .GET()
            .build()

    private fun buildDeleteRequest(
        path: String,
        bearerToken: String,
    ): HttpRequest =
        HttpRequest
            .newBuilder()
            .uri(URI.create("$base$path"))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer $bearerToken")
            .header("Content-Type", "application/json")
            .DELETE()
            .build()

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

    private enum class HttpMethod {
        POST,
        PUT,
    }
}

class ApiException(
    statusCode: Int,
    responseBody: String,
) : RuntimeException("API returned $statusCode: $responseBody")
