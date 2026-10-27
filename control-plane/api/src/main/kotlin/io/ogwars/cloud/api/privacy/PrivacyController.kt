package io.ogwars.cloud.api.privacy

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.audit.NotAudited
import io.ogwars.cloud.api.privacy.Privacy.Companion.ERASURE
import io.ogwars.cloud.api.privacy.Privacy.Companion.PLAYER
import io.ogwars.cloud.api.privacy.Privacy.Companion.PRIVACY
import io.ogwars.cloud.api.privacy.Privacy.Companion.USER
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Export and erasure of personal data (D-0049, D-0107); the service writes the audit entries itself. */
@RestController
@RequestMapping(PRIVACY)
class PrivacyController(
    private val privacy: Privacy,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.PRIVACY_EXPORT)
    @GetMapping(PLAYER)
    fun exportPlayer(
        @PathVariable uuid: UUID,
    ): PlayerExport = privacy.exportPlayer(actors.current(), uuid)

    /** 409 while the player is online. */
    @Requires(ApiPermission.PRIVACY_ERASE)
    @NotAudited
    @PostMapping(PLAYER + ERASURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun erasePlayer(
        @PathVariable uuid: UUID,
    ) = privacy.erasePlayer(actors.current(), uuid)

    @Requires(ApiPermission.PRIVACY_EXPORT)
    @GetMapping(USER)
    fun exportUser(
        @PathVariable id: UUID,
    ): UserExport = privacy.exportUser(actors.current(), id)

    @Requires(ApiPermission.PRIVACY_ERASE)
    @NotAudited
    @PostMapping(USER + ERASURE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun eraseUser(
        @PathVariable id: UUID,
    ) = privacy.eraseUser(actors.current(), id)
}
