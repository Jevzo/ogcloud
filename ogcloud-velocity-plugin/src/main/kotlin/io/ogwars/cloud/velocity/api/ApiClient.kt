package io.ogwars.cloud.velocity.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.slf4j.Logger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class ApiServerResponse(
    val id: String,
    val group: String,
    val type: String,
    val displayName: String,
    val state: String,
    val gameState: String?,
    val podName: String,
    val podIp: String?,
    val port: Int,
    val templateVersion: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val tps: Double,
    val memoryUsedMb: Long,
    val startedAt: String?,
    val lastHeartbeat: String?
)

data class ApiScalingConfig(
    val minOnline: Int,
    val maxInstances: Int,
    val playersPerServer: Int,
    val scaleUpThreshold: Double,
    val scaleDownThreshold: Double,
    val cooldownSeconds: Int
)

data class ApiResourceConfig(
    val memoryRequest: String, val memoryLimit: String, val cpuRequest: String, val cpuLimit: String
)

data class ApiGroupResponse(
    val id: String,
    val type: String,
    val templateBucket: String,
    val templatePath: String,
    val templateVersion: String,
    val scaling: ApiScalingConfig,
    val resources: ApiResourceConfig,
    val jvmFlags: String,
    val drainTimeoutSeconds: Int,
    val serverImage: String,
    val storageSize: String,
    val maintenance: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class ApiOnlinePlayerResponse(
    val uuid: String,
    val name: String,
    val proxyId: String?,
    val proxyDisplayName: String?,
    val serverId: String?,
    val serverDisplayName: String?,
    val groupId: String?,
    val connectedAt: String?
)

data class ApiPlayerResponse(
    val uuid: String,
    val name: String,
    val permission: ApiPermissionConfig,
    val firstJoin: String,
    val online: Boolean,
    val proxyId: String?,
    val proxyDisplayName: String?,
    val serverId: String?,
    val serverDisplayName: String?,
    val connectedAt: String?
)

data class ApiPermissionConfig(
    val group: String, val length: Long, val endMillis: Long
)

data class ApiPermissionGroupResponse(
    val id: String,
    val name: String,
    val display: ApiDisplayConfig,
    val weight: Int,
    val default: Boolean,
    val permissions: List<String>
)

data class ApiDisplayConfig(
    val chatPrefix: String, val chatSuffix: String, val nameColor: String, val tabPrefix: String
)

data class ApiTablistSettings(val header: String, val footer: String)

data class ApiNetworkSettingsResponse(
    val motd: ApiMotdSettings,
    val versionName: ApiVersionNameSettings,
    val maxPlayers: Int,
    val defaultGroup: String,
    val maintenance: Boolean,
    val maintenanceKickMessage: String,
    val tablist: ApiTablistSettings
)

data class ApiMotdSettings(val global: String, val maintenance: String)
data class ApiVersionNameSettings(val global: String, val maintenance: String)

data class ApiServerRequestResponse(
    val serverId: String, val group: String
)

data class ApiTemplateInfo(
    val group: String, val version: String, val path: String
)

data class ApiWebUserResponse(
    val id: String, val email: String, val username: String, val role: String, val linkedPlayerUuid: String?
)

data class ApiPaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val offset: Int,
    val totalItems: Int,
    val totalPages: Int,
    val hasNext: Boolean
)

data class ApiAuthTokenResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: ApiWebUserResponse
)

class ApiClient(
    baseUrl: String, private val authEmail: String, private val authPassword: String, private val logger: Logger
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

    fun listServers(group: String? = null): CompletableFuture<List<ApiServerResponse>> {
        val path = if (group != null) {
            "/api/v1/servers?group=${encodeQueryParam(group)}"
        } else {
            "/api/v1/servers"
        }
        return getAllPaged(path, object : TypeToken<ApiPaginatedResponse<ApiServerResponse>>() {}.type)
    }

    fun getServer(id: String): CompletableFuture<ApiServerResponse> {
        return get("/api/v1/servers/$id", ApiServerResponse::class.java)
    }

    fun requestServer(group: String): CompletableFuture<ApiServerRequestResponse> {
        return postWithResponse(
            "/api/v1/servers/request", mapOf("group" to group), ApiServerRequestResponse::class.java
        )
    }

    fun stopServer(id: String): CompletableFuture<Void> {
        return post("/api/v1/servers/$id/stop", null)
    }

    fun killServer(id: String): CompletableFuture<Void> {
        return post("/api/v1/servers/$id/kill", null)
    }

    fun forceTemplatePush(id: String): CompletableFuture<Void> {
        return post("/api/v1/servers/$id/template/push", null)
    }

    fun listGroups(): CompletableFuture<List<ApiGroupResponse>> {
        return getAllPaged("/api/v1/groups", object : TypeToken<ApiPaginatedResponse<ApiGroupResponse>>() {}.type)
    }

    fun getGroup(id: String): CompletableFuture<ApiGroupResponse> {
        return get("/api/v1/groups/$id", ApiGroupResponse::class.java)
    }

    fun setGroupMaintenance(id: String, enabled: Boolean): CompletableFuture<Void> {
        return put("/api/v1/groups/$id/maintenance", mapOf("maintenance" to enabled))
    }

    fun listOnlinePlayers(
        name: String? = null, serverId: String? = null
    ): CompletableFuture<List<ApiOnlinePlayerResponse>> {
        val params = mutableListOf<String>()
        if (name != null) params.add("name=${encodeQueryParam(name)}")
        if (serverId != null) params.add("serverId=${encodeQueryParam(serverId)}")
        val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
        return getAllPaged(
            "/api/v1/players$query", object : TypeToken<ApiPaginatedResponse<ApiOnlinePlayerResponse>>() {}.type
        )
    }

    fun getPlayer(uuid: String): CompletableFuture<ApiPlayerResponse> {
        return get("/api/v1/players/$uuid", ApiPlayerResponse::class.java)
    }

    fun setPlayerGroup(uuid: String, group: String, duration: String): CompletableFuture<ApiPlayerResponse> {
        return putWithResponse(
            "/api/v1/players/$uuid/group",
            mapOf("group" to group, "duration" to duration),
            ApiPlayerResponse::class.java
        )
    }

    fun transferPlayer(uuid: String, target: String): CompletableFuture<Void> {
        return post("/api/v1/players/$uuid/transfer", mapOf("target" to target))
    }

    fun getNetworkSettings(): CompletableFuture<ApiNetworkSettingsResponse> {
        return get("/api/v1/network", ApiNetworkSettingsResponse::class.java)
    }

    fun setNetworkMaintenance(enabled: Boolean): CompletableFuture<Void> {
        return put("/api/v1/network/maintenance", mapOf("maintenance" to enabled))
    }

    fun updateNetwork(updates: Map<String, Any?>): CompletableFuture<Void> {
        return put("/api/v1/network", updates)
    }

    fun listPermissionGroups(): CompletableFuture<List<ApiPermissionGroupResponse>> {
        return getAllPaged(
            "/api/v1/permissions/groups", object : TypeToken<ApiPaginatedResponse<ApiPermissionGroupResponse>>() {}.type
        )
    }

    fun getPermissionGroup(id: String): CompletableFuture<ApiPermissionGroupResponse> {
        return get("/api/v1/permissions/groups/$id", ApiPermissionGroupResponse::class.java)
    }

    fun createPermissionGroup(body: Map<String, Any?>): CompletableFuture<ApiPermissionGroupResponse> {
        return postWithResponse("/api/v1/permissions/groups", body, ApiPermissionGroupResponse::class.java)
    }

    fun deletePermissionGroup(id: String): CompletableFuture<Void> {
        return delete("/api/v1/permissions/groups/$id")
    }

    fun addPermission(groupId: String, permission: String): CompletableFuture<ApiPermissionGroupResponse> {
        return postWithResponse(
            "/api/v1/permissions/groups/$groupId/permissions",
            mapOf("permission" to permission),
            ApiPermissionGroupResponse::class.java
        )
    }

    fun removePermission(groupId: String, permission: String): CompletableFuture<Void> {
        return delete("/api/v1/permissions/groups/$groupId/permissions/$permission")
    }

    fun executeCommand(target: String, targetType: String, command: String): CompletableFuture<Void> {
        return post("/api/v1/command", mapOf("target" to target, "targetType" to targetType, "command" to command))
    }

    private fun <T> get(path: String, type: java.lang.reflect.Type): CompletableFuture<T> {
        return send(
            requestBuilder(path, authorize = true).GET().build()
        ).thenApply { response -> gson.fromJson(response.body(), type) }
    }

    private fun <T> getAllPaged(path: String, type: java.lang.reflect.Type): CompletableFuture<List<T>> {
        return getAllPaged(path, 0, mutableListOf<T>(), type)
    }

    private fun <T> getAllPaged(
        path: String, page: Int, accumulator: MutableList<T>, type: java.lang.reflect.Type
    ): CompletableFuture<List<T>> {
        return get<ApiPaginatedResponse<T>>(withPageParams(path, page), type).thenCompose { response ->
            accumulator.addAll(response.items)

            if (!response.hasNext) {
                CompletableFuture.completedFuture(accumulator.toList())
            } else {
                getAllPaged(path, page + 1, accumulator, type)
            }
        }
    }

    private fun post(path: String, body: Any?): CompletableFuture<Void> {
        return send(buildJsonRequest(path, body, HttpMethod.POST)).thenApply { null }
    }

    private fun <T> postWithResponse(path: String, body: Any?, type: Class<T>): CompletableFuture<T> {
        return send(
            buildJsonRequest(
                path, body, HttpMethod.POST
            )
        ).thenApply { response -> gson.fromJson(response.body(), type) }
    }

    private fun put(path: String, body: Any?): CompletableFuture<Void> {
        return send(buildJsonRequest(path, body, HttpMethod.PUT)).thenApply { null }
    }

    private fun <T> putWithResponse(path: String, body: Any?, type: Class<T>): CompletableFuture<T> {
        return send(buildJsonRequest(path, body, HttpMethod.PUT)).thenApply { response ->
            gson.fromJson(
                response.body(), type
            )
        }
    }

    private fun delete(path: String): CompletableFuture<Void> {
        return send(requestBuilder(path, authorize = true).DELETE().build()).thenApply { null }
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
                    return accessToken ?: throw IllegalStateException("Refresh succeeded without access token")
                } catch (ex: Exception) {
                    logger.warn("API token refresh failed, falling back to login: {}", ex.message)
                }
            }

            loginSync()

            return accessToken ?: throw IllegalStateException("Login succeeded without access token")
        }
    }

    private fun loginSync() {
        val response = sendAuthRequest("/api/v1/auth/login", mapOf("email" to authEmail, "password" to authPassword))
        setTokens(response)

        logger.info("Authenticated Velocity API client with web account {}", response.user.email)
    }

    private fun refreshSync(currentRefreshToken: String) {
        val response = sendAuthRequest("/api/v1/auth/refresh", mapOf("refreshToken" to currentRefreshToken))
        setTokens(response)
    }

    private fun sendAuthRequest(path: String, body: Any): ApiAuthTokenResponse {
        val response = httpClient.send(
            buildJsonRequest(path, body, HttpMethod.POST, authorize = false), HttpResponse.BodyHandlers.ofString()
        ).validated()

        return gson.fromJson(response.body(), ApiAuthTokenResponse::class.java)
    }

    private fun setTokens(response: ApiAuthTokenResponse) {
        accessToken = response.accessToken
        accessTokenExpiresAt = Instant.parse(response.accessTokenExpiresAt)
        refreshToken = response.refreshToken
        refreshTokenExpiresAt = Instant.parse(response.refreshTokenExpiresAt)
    }

    private fun send(request: HttpRequest): CompletableFuture<HttpResponse<String>> {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response -> response.validated() }
    }

    private fun buildJsonRequest(
        path: String, body: Any?, method: HttpMethod, authorize: Boolean = true
    ): HttpRequest {
        val builder = requestBuilder(path, authorize).header("Content-Type", "application/json")

        when (method) {
            HttpMethod.POST -> builder.POST(HttpRequest.BodyPublishers.ofString(serializeBody(body)))
            HttpMethod.PUT -> builder.PUT(HttpRequest.BodyPublishers.ofString(serializeBody(body)))
        }

        return builder.build()
    }

    private fun encodeQueryParam(value: String): String {
        return encode(value)
    }

    private fun withPageParams(path: String, page: Int): String {
        val separator = if (path.contains("?")) "&" else "?"
        return "${path}${separator}page=$page&size=$MAX_LIST_PAGE_SIZE"
    }

    private fun requestBuilder(path: String, authorize: Boolean = false): HttpRequest.Builder {
        return HttpRequest.newBuilder().uri(URI.create("$base$path")).timeout(REQUEST_TIMEOUT).apply {
            if (authorize) {
                header("Authorization", "Bearer ${ensureAccessToken()}")
            }
        }
    }

    private fun HttpResponse<String>.validated(): HttpResponse<String> {
        if (statusCode() !in 200..299) {
            throw ApiException(statusCode(), body())
        }
        return this
    }

    private fun serializeBody(body: Any?): String = gson.toJson(body ?: emptyMap<String, Any>())

    private fun encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private enum class HttpMethod {
        POST, PUT
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val AUTH_EXPIRY_SKEW: Duration = Duration.ofSeconds(30)
        private const val MAX_LIST_PAGE_SIZE = 200
    }
}

class ApiException(statusCode: Int, responseBody: String) : RuntimeException("API returned $statusCode: $responseBody")
