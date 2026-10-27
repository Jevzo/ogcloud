package io.ogwars.cloud.api.permissions

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.permission.PermissionGroup
import io.ogwars.cloud.domain.permission.PlayerRank
import io.ogwars.cloud.domain.permission.RankDisplay
import io.ogwars.cloud.domain.store.Revisioned
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
import java.time.Instant
import java.util.UUID

/** Colours use `&` codes. */
data class RankDisplayView(
    val chatPrefix: String,
    val chatSuffix: String,
    val nameColor: String,
    val tabPrefix: String,
)

data class PermissionGroupSettings(
    val name: String,
    /** Higher weights sort first in the tablist. */
    val weight: Int,
    /** Setting it makes this the default group; the previous default loses the flag. */
    val defaultGroup: Boolean,
    val display: RankDisplayView,
    /** Permission node to its description; `*` grants everything. */
    val permissions: Map<String, String>,
) {
    fun toDomain(id: String) =
        PermissionGroup(
            id,
            name,
            weight,
            permissions,
            RankDisplay(display.chatPrefix, display.chatSuffix, display.nameColor, display.tabPrefix),
            defaultGroup,
        )
}

data class PermissionGroupView(
    val id: String,
    val settings: PermissionGroupSettings,
    val version: Long,
)

data class CreatePermissionGroupRequest(
    val id: String,
    val settings: PermissionGroupSettings,
)

data class UpdatePermissionGroupRequest(
    val settings: PermissionGroupSettings,
    /** The version read; a stale one is refused with 409. */
    val version: Long,
)

data class SetRankRequest(
    val permissionGroupId: String,
    /** Like v1: "1Y 2M 3w 4d 5h 6m 7s", or "-1" for a permanent rank. */
    val duration: String,
)

data class RankView(
    val permissionGroupId: String,
    /** Null for a permanent rank. */
    val expiresAt: Instant?,
)

private fun Revisioned<PermissionGroup>.toView() =
    with(value) {
        PermissionGroupView(
            id,
            PermissionGroupSettings(
                name,
                weight,
                isDefault,
                RankDisplayView(display.chatPrefix, display.chatSuffix, display.nameColor, display.tabPrefix),
                permissions,
            ),
            revision,
        )
    }

private fun PlayerRank.toView() = RankView(groupId, expiresAt)

@RestController
@RequestMapping("/api/v1/permission-groups")
class PermissionGroupController(
    private val groups: PermissionGroups,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.PERMISSIONS_READ)
    @GetMapping
    fun list(): List<PermissionGroupView> = groups.list().map { it.toView() }

    @Requires(ApiPermission.PERMISSIONS_READ)
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
    ): PermissionGroupView = groups.get(id).toView()

    @Requires(ApiPermission.PERMISSIONS_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreatePermissionGroupRequest,
    ): PermissionGroupView = groups.create(actors.current(), request.settings.toDomain(request.id)).toView()

    @Requires(ApiPermission.PERMISSIONS_WRITE)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdatePermissionGroupRequest,
    ): PermissionGroupView = groups.update(actors.current(), request.version, request.settings.toDomain(id)).toView()

    /** Holders move to the default group (D-0102). */
    @Requires(ApiPermission.PERMISSIONS_WRITE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
    ) = groups.delete(actors.current(), id)
}

@RestController
@RequestMapping("/api/v1/players/{uuid}/rank")
class RankController(
    private val groups: PermissionGroups,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.PERMISSIONS_WRITE)
    @PutMapping
    fun set(
        @PathVariable uuid: UUID,
        @RequestBody request: SetRankRequest,
    ): RankView = groups.setRank(actors.current(), uuid, request.permissionGroupId, request.duration).toView()
}
