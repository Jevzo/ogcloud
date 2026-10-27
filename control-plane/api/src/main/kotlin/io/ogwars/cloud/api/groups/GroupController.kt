package io.ogwars.cloud.api.groups

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.jobs.JobView
import io.ogwars.cloud.api.jobs.toView
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.group.GroupId
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

@RestController
@RequestMapping("/api/v1/groups")
class GroupController(
    private val groups: GroupManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.GROUPS_READ)
    @GetMapping
    fun list(): List<GroupView> = groups.list().map { it.toView() }

    /** Soft-deleted groups, restorable until their purge. */
    @Requires(ApiPermission.GROUPS_READ)
    @GetMapping("/deleted")
    fun deleted(): List<DeletedGroupView> = groups.deleted().map { it.toView() }

    @Requires(ApiPermission.GROUPS_READ)
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
    ): GroupView = groups.get(GroupId(id)).toView()

    @Requires(ApiPermission.GROUPS_WRITE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: CreateGroupRequest,
    ): GroupView = groups.create(actors.current(), GroupId(request.id), request.type, request.settings).toView()

    @Requires(ApiPermission.GROUPS_WRITE)
    @PutMapping("/{id}")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateGroupRequest,
    ): GroupView = groups.update(actors.current(), GroupId(id), request.version, request.settings).toView()

    @Requires(ApiPermission.GROUPS_WRITE)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun delete(
        @PathVariable id: String,
    ): JobView = groups.delete(actors.current(), GroupId(id)).toView()

    @Requires(ApiPermission.GROUPS_WRITE)
    @PostMapping("/{id}/restore")
    fun restore(
        @PathVariable id: String,
    ): GroupView = groups.restore(actors.current(), GroupId(id)).toView()

    @Requires(ApiPermission.GROUPS_WRITE)
    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun restart(
        @PathVariable id: String,
        @RequestBody request: RestartRequest,
    ): JobView = groups.restart(actors.current(), GroupId(id), request.batchSize).toView()
}
