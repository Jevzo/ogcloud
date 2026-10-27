package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.auth.UserPermissions
import io.ogwars.cloud.api.web.Anonymous
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.api.web.Cursors
import io.ogwars.cloud.api.web.Page
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val users: UserManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.USERS_READ)
    @GetMapping
    fun list(
        @RequestParam cursor: String?,
        @RequestParam limit: Int?,
    ): Page<UserView> {
        val size = Cursors.limit(limit)
        val page = Cursors.page(users.list(cursor?.let(Cursors::decode), size + 1), size) { it.value.id }
        return Page(page.items.map { it.toView() }, page.nextCursor)
    }

    @Requires(ApiPermission.USERS_READ)
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): UserView = users.get(id).toView()

    @Requires(ApiPermission.USERS_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateUserRequest,
    ): UserView =
        users.create(actors.current(), request.username, request.email, request.password, request.roleId).toView()

    @Requires(ApiPermission.USERS_WRITE)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateUserRequest,
    ): UserView =
        users
            .update(
                actors.current(),
                id,
                request.version,
                request.username,
                request.email,
                request.roleId,
                request.enabled,
            ).toView()

    @Requires(ApiPermission.USERS_WRITE)
    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(
        @PathVariable id: UUID,
        @RequestBody request: PasswordRequest,
    ) = users.resetPassword(actors.current(), id, request.password)

    @Requires(ApiPermission.USERS_WRITE)
    @DeleteMapping("/{id}/logins")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeLogins(
        @PathVariable id: UUID,
    ) = users.revokeLogins(actors.current(), id)

    @Requires(ApiPermission.USERS_WRITE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = users.delete(actors.current(), id)
}

@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val users: UserManagement,
    private val permissions: UserPermissions,
    private val actors: CurrentActor,
) {
    @AnyCaller
    @GetMapping
    fun get(): MeView {
        val user = users.self(actors.current())
        return MeView(
            user.id,
            user.username,
            user.email,
            user.roleId,
            permissions.of(user).map { it.scope }.sorted(),
            user.linkedPlayerUuid,
        )
    }

    @AnyCaller
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
    ) = users.changeOwnPassword(actors.current(), request.currentPassword, request.newPassword)
}

@RestController
@RequestMapping("/api/v1/setup")
class SetupController(
    private val setup: Setup,
    private val users: UserManagement,
) {
    @Anonymous
    @GetMapping
    fun status(): SetupStatus = SetupStatus(setup.pending())

    @Anonymous
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun complete(
        @RequestBody request: SetupRequest,
    ): UserView =
        users.get(setup.complete(request.setupToken, request.username, request.email, request.password).id).toView()
}
