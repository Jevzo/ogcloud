package io.ogwars.cloud.paper.api

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
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

    fun getNetworkSettingsSync(): ApiNetworkSettingsResponse {
        val response = sendGetRequest("/api/v1/network", ensureAccessToken())
        return gson.fromJson(response.body, ApiNetworkSettingsResponse::class.java)
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
        sendAuthorizedPost(path, body).thenApply { response ->
            gson.fromJson(response.body, type)
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

    private fun sendAuthRequest(
        path: String,
        body: Any,
    ): ApiAuthTokenResponse {
        val response = sendPostRequest(path, body, null)
        return gson.fromJson(response.body, ApiAuthTokenResponse::class.java)
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
    ): CompletableFuture<ApiHttpResponse> =
        CompletableFuture.supplyAsync {
            sendPostRequest(path, body, ensureAccessToken())
        }

    private fun sendPostRequest(
        path: String,
        body: Any?,
        bearerToken: String?,
    ): ApiHttpResponse {
        val connection = openConnection(path, "POST", bearerToken)

        return try {
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(serializeBody(body))
            }

            readResponse(connection).validated()
        } finally {
            connection.disconnect()
        }
    }

    private fun sendGetRequest(
        path: String,
        bearerToken: String,
    ): ApiHttpResponse {
        val connection = openConnection(path, "GET", bearerToken)

        return try {
            readResponse(connection).validated()
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        path: String,
        method: String,
        bearerToken: String?,
    ): HttpURLConnection {
        val connection = URL("$base$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        connection.setRequestProperty("Content-Type", "application/json")

        if (bearerToken != null) {
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
        }

        return connection
    }

    private fun readResponse(connection: HttpURLConnection): ApiHttpResponse {
        val statusCode = connection.responseCode
        val stream = selectResponseStream(connection, statusCode)
        val body = stream?.use(::readFully).orEmpty()

        return ApiHttpResponse(statusCode, body)
    }

    private fun selectResponseStream(
        connection: HttpURLConnection,
        statusCode: Int,
    ): InputStream? =
        if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }

    private fun readFully(stream: InputStream): String =
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

    private fun serializeBody(body: Any?): String = gson.toJson(body ?: emptyMap<String, Any>())

    private fun ApiHttpResponse.validated(): ApiHttpResponse {
        if (statusCode !in 200..299) {
            throw ApiException(statusCode, body)
        }

        return this
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
        private val AUTH_EXPIRY_SKEW: Duration = Duration.ofSeconds(30)

        private val CONNECT_TIMEOUT_MS: Int = CONNECT_TIMEOUT.toMillis().toInt()
        private val REQUEST_TIMEOUT_MS: Int = REQUEST_TIMEOUT.toMillis().toInt()
    }
}

data class ApiHttpResponse(
    val statusCode: Int,
    val body: String,
)

class ApiException(
    statusCode: Int,
    responseBody: String,
) : RuntimeException("API returned $statusCode: $responseBody")
