package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.auth.UserPermissions
import io.ogwars.cloud.api.auth.mfa.TotpEnrollment
import io.ogwars.cloud.api.auth.mfa.TotpService
import io.ogwars.cloud.api.link.LinkCodeSent
import io.ogwars.cloud.api.link.MinecraftLinks
import io.ogwars.cloud.api.web.Anonymous
import io.ogwars.cloud.api.web.AnyCaller
import io.ogwars.cloud.api.web.Cursors
import io.ogwars.cloud.api.web.Page
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.store.Revisioned
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.WebUser
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
    private val links: MinecraftLinks,
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
        return Page(views(page.items), page.nextCursor)
    }

    @Requires(ApiPermission.USERS_READ)
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): UserView = view(users.get(id))

    @Requires(ApiPermission.USERS_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateUserRequest,
    ): UserView =
        view(users.create(actors.current(), request.username, request.email, request.password, request.roleId))

    @Requires(ApiPermission.USERS_WRITE)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdateUserRequest,
    ): UserView =
        view(
            users.update(
                actors.current(),
                id,
                request.version,
                request.username,
                request.email,
                request.roleId,
                request.enabled,
            ),
        )

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

    @Requires(ApiPermission.USERS_WRITE)
    @DeleteMapping("/{id}/minecraft-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlinkMinecraft(
        @PathVariable id: UUID,
    ) = links.unlink(actors.current(), id)

    private fun views(page: List<Revisioned<WebUser>>): List<UserView> {
        val names = links.linkedNames(page.map { it.value })
        return page.map { it.toView(it.value.linkedPlayerUuid?.let(names::get)) }
    }

    private fun view(user: Revisioned<WebUser>) = views(listOf(user)).single()
}

@RestController
@RequestMapping("/api/v1/me")
class MeController(
    private val users: UserManagement,
    private val permissions: UserPermissions,
    private val totp: TotpService,
    private val links: MinecraftLinks,
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
            links.linkedNames(listOf(user)).values.firstOrNull(),
            totp.isEnrolled(user.id),
        )
    }

    /** Sends a code to the player in game; the player must be online (D-0106). */
    @AnyCaller
    @PostMapping("/minecraft-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun requestMinecraftLink(
        @RequestBody request: MinecraftLinkRequest,
    ): LinkCodeSent {
        val actor = actors.current()
        return links.request(actor, users.self(actor).id, request.playerName)
    }

    /** One try per code. Permissions that depend on the link need a new access token. */
    @AnyCaller
    @PostMapping("/minecraft-link/confirm")
    fun confirmMinecraftLink(
        @RequestBody request: MinecraftLinkConfirmation,
    ): MeView {
        links.confirm(users.self(actors.current()).id, request.code)
        return get()
    }

    @AnyCaller
    @DeleteMapping("/minecraft-link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlinkMinecraft() {
        val actor = actors.current()
        links.unlink(actor, users.self(actor).id)
    }

    /** Starts adding an authenticator app; confirm it with a first code (D-0097). */
    @AnyCaller
    @PostMapping("/totp")
    fun startTotp(): TotpEnrollment = totp.startEnrollment(users.self(actors.current()))

    @AnyCaller
    @PostMapping("/totp/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmTotp(
        @RequestBody request: TotpCodeRequest,
    ) = totp.confirm(users.self(actors.current()).id, request.code)

    /** Needs a current code from the authenticator (D-0108). */
    @AnyCaller
    @DeleteMapping("/totp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeTotp(
        @RequestBody request: TotpCodeRequest,
    ) = totp.remove(users.self(actors.current()), request.code)

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
        users
            .get(setup.complete(request.setupToken, request.username, request.email, request.password).id)
            .toView(linkedPlayerName = null)
}
