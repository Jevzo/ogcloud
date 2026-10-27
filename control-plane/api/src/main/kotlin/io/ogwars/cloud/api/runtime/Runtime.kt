package io.ogwars.cloud.api.runtime

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.groups.RestartRequest
import io.ogwars.cloud.api.groups.batchPayload
import io.ogwars.cloud.api.jobs.JobManagement
import io.ogwars.cloud.api.jobs.JobView
import io.ogwars.cloud.api.jobs.toView
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.job.JobKinds
import io.ogwars.cloud.domain.runtime.Platform
import io.ogwars.cloud.domain.runtime.ProfileRole
import io.ogwars.cloud.domain.runtime.RuntimeProfile
import io.ogwars.cloud.domain.runtime.RuntimeProfiles
import io.ogwars.cloud.domain.store.RuntimeArtifactStore
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class RuntimeArtifactView(
    /** Relative to the server directory, e.g. `plugins/ViaVersion.jar`. */
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
    val sourceUrl: String,
    val storedAt: Instant,
)

data class RuntimeProfileView(
    val id: String,
    val platform: Platform,
    val role: ProfileRole,
    val version: String,
    val protocol: Int,
    val javaVersion: Int,
    /** The bundle the controller keeps for this profile (D-0079). */
    val artifacts: List<RuntimeArtifactView>,
)

/** Runtime profiles and their bundles; a rollout replaces a profile's servers, like v1's runtime refresh (D-0104). */
@RestController
@RequestMapping("/api/v1/runtime")
class RuntimeController(
    private val artifacts: RuntimeArtifactStore,
    private val jobs: JobManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.NETWORK_READ)
    @GetMapping
    fun list(): List<RuntimeProfileView> =
        RuntimeProfiles.ALL.map { profile ->
            RuntimeProfileView(
                profile.id,
                profile.platform,
                profile.role,
                profile.version,
                profile.protocol,
                profile.javaVersion,
                artifacts.findByProfile(profile.id).sortedBy { it.path }.map {
                    RuntimeArtifactView(it.path, it.sha256, it.sizeBytes, it.sourceUrl, it.storedAt)
                },
            )
        }

    @Requires(ApiPermission.NETWORK_WRITE)
    @PostMapping("/{profileId}/rollout")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun rollout(
        @PathVariable profileId: String,
        @RequestBody request: RestartRequest,
    ): JobView {
        val profile =
            RuntimeProfile.find(profileId) ?: throw NoSuchElementException("Unknown runtime profile $profileId")
        return jobs
            .start(
                actors.current(),
                JobKinds.BUNDLE_ROLLOUT,
                mapOf(JobKinds.PROFILE_ID to profile.id) + batchPayload(request.batchSize),
            ).toView()
    }
}
