package io.ogwars.cloud.api.auth.users

import io.ogwars.cloud.api.audit.CurrentActor
import io.ogwars.cloud.api.network.NetworkManagement
import io.ogwars.cloud.api.web.Requires
import io.ogwars.cloud.domain.network.NetworkSettings
import io.ogwars.cloud.domain.user.ApiPermission
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Who may use the dashboard beyond holding a role (D-0229). */
data class SignInRulesView(
    /** While set, a user without a linked Minecraft account holds no permissions and sees only the link flow (D-0094). */
    val requireMinecraftLink: Boolean,
)

private fun NetworkSettings.toSignInRules() = SignInRulesView(requireMinecraftLink)

const val SIGN_IN_RULES_PATH = "/api/v1/users/sign-in-rules"

@RestController
@RequestMapping(SIGN_IN_RULES_PATH)
class SignInRulesController(
    private val network: NetworkManagement,
    private val actors: CurrentActor,
) {
    @Requires(ApiPermission.USERS_READ)
    @GetMapping
    fun get(): SignInRulesView = network.get().value.toSignInRules()

    /** Owners only, checked in [NetworkManagement.requireMinecraftLink]. */
    @Requires(ApiPermission.USERS_WRITE)
    @PutMapping
    fun update(
        @RequestBody request: SignInRulesView,
    ): SignInRulesView =
        network.requireMinecraftLink(actors.current(), request.requireMinecraftLink).value.toSignInRules()
}
