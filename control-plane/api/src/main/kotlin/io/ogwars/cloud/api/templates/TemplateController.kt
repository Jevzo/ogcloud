package io.ogwars.cloud.api.templates

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.group.GroupId
import io.ogwars.cloud.domain.template.TemplateSource
import io.ogwars.cloud.domain.template.TemplateVersion
import io.ogwars.cloud.domain.user.ApiPermission
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
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
import io.swagger.v3.oas.annotations.parameters.RequestBody as ApiRequestBody

data class TemplateVersionView(
    val id: UUID,
    val sha256: String,
    val sizeBytes: Long,
    val source: TemplateSource,
    /** e.g. `user:0192…` or `system:template-agent` */
    val createdBy: String,
    val createdAt: Instant,
    /** New servers of the group start from this version. */
    val active: Boolean,
)

data class PromoteRequest(
    val versionId: UUID,
)

private fun TemplateVersion.toView(activeId: UUID?) =
    TemplateVersionView(id, sha256, sizeBytes, source, createdBy.toString(), createdAt, id == activeId)

@RestController
@RequestMapping("/api/v1/groups/{groupId}/templates")
class TemplateController(
    private val templates: Templates,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.TEMPLATES_READ)
    @GetMapping
    fun list(
        @PathVariable groupId: String,
    ): List<TemplateVersionView> {
        val (group, versions) = templates.list(GroupId(groupId))
        return versions.map { it.toView(group.activeTemplateVersionId) }
    }

    /** The raw `.tar.gz`, streamed into storage (D-0087). STATIC groups activate it at once. */
    @Requires(ApiPermission.TEMPLATES_WRITE)
    @PostMapping(consumes = [GZIP])
    @ResponseStatus(HttpStatus.CREATED)
    @ApiRequestBody(content = [Content(mediaType = GZIP, schema = Schema(type = "string", format = "binary"))])
    fun upload(
        @PathVariable groupId: String,
        request: HttpServletRequest,
    ): TemplateVersionView {
        val group = GroupId(groupId)
        val version = templates.upload(actors.current(), group, request.inputStream, request.contentLengthLong)
        return version.toView(templates.list(group).first.activeTemplateVersionId)
    }

    @Requires(ApiPermission.TEMPLATES_READ)
    @GetMapping("/{versionId}/content", produces = [GZIP])
    fun download(
        @PathVariable groupId: String,
        @PathVariable versionId: UUID,
        response: HttpServletResponse,
    ) {
        response.contentType = GZIP
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$groupId-$versionId.tar.gz\"")
        templates.download(GroupId(groupId), versionId, response.outputStream)
    }

    /** Promotes a version, or rolls back to an older one (D-0042). */
    @Requires(ApiPermission.TEMPLATES_WRITE)
    @PutMapping("/active")
    fun promote(
        @PathVariable groupId: String,
        @RequestBody request: PromoteRequest,
    ): TemplateVersionView =
        templates.promote(actors.current(), GroupId(groupId), request.versionId).toView(request.versionId)

    @Requires(ApiPermission.TEMPLATES_WRITE)
    @DeleteMapping("/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable groupId: String,
        @PathVariable versionId: UUID,
    ) = templates.delete(GroupId(groupId), versionId)

    private companion object {
        const val GZIP = "application/gzip"
    }
}
