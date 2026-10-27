package io.ogwars.cloud.api.auth.clients

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class OAuthClientRequest(
    val name: String,
    val scopes: List<String>,
)

@RestController
@RequestMapping("/api/v1/oauth-clients")
class OAuthClientController(
    private val clients: OAuthClientManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.OAUTH_CLIENTS_READ)
    @GetMapping
    fun list(): List<OAuthClientView> = clients.list()

    @Requires(ApiPermission.OAUTH_CLIENTS_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: OAuthClientRequest,
    ): OAuthClientCredentials = clients.create(actors.current(), request.name, request.scopes)

    @Requires(ApiPermission.OAUTH_CLIENTS_WRITE)
    @PutMapping("/{clientId}")
    fun update(
        @PathVariable clientId: String,
        @RequestBody request: OAuthClientRequest,
    ): OAuthClientView = clients.update(actors.current(), clientId, request.name, request.scopes)

    @Requires(ApiPermission.OAUTH_CLIENTS_WRITE)
    @PostMapping("/{clientId}/secret")
    fun rotateSecret(
        @PathVariable clientId: String,
    ): OAuthClientCredentials = clients.rotateSecret(clientId)

    @Requires(ApiPermission.OAUTH_CLIENTS_WRITE)
    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable clientId: String,
    ) = clients.delete(clientId)
}
