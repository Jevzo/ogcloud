package io.ogwars.cloud.api.auth.roles

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.Role
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

data class RoleView(
    val id: String,
    val name: String,
    val builtIn: Boolean,
    val permissions: List<String>,
    val version: Long,
)

data class CreateRoleRequest(
    val id: String,
    val name: String,
    val permissions: List<String>,
)

data class UpdateRoleRequest(
    val name: String,
    val permissions: List<String>,
    val version: Long,
)

private fun Revisioned<Role>.toView() =
    RoleView(
        value.id,
        value.name,
        value.builtIn,
        value.permissions
            .map {
                it.scope
            }.sorted(),
        revision,
    )

@RestController
@RequestMapping("/api/v1/roles")
class RoleController(
    private val roles: RoleManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.ROLES_READ)
    @GetMapping
    fun list(): List<RoleView> = roles.list().map { it.toView() }

    /** The permission catalog (D-0092). */
    @Requires(ApiPermission.ROLES_READ)
    @GetMapping("/permissions")
    fun permissions(): List<String> = ApiPermission.entries.map { it.scope }

    @Requires(ApiPermission.ROLES_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateRoleRequest,
    ): RoleView = roles.create(actors.current(), request.id, request.name, request.permissions).toView()

    @Requires(ApiPermission.ROLES_WRITE)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateRoleRequest,
    ): RoleView = roles.update(actors.current(), id, request.version, request.name, request.permissions).toView()

    @Requires(ApiPermission.ROLES_WRITE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
    ) = roles.delete(actors.current(), id)
}
