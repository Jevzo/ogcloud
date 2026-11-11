package io.ogwars.cloud.api.auth.mfa

import io.ogwars.cloud.domain.store.ConflictException
import io.ogwars.cloud.domain.store.TotpStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.WebUser
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Removes one of a user's second factors while no other removal of theirs runs, so an owner always keeps one
 * (D-0097): two removals at once both counted two factors and left none.
 */
@Component
class FactorGuard(
    private val users: WebUserStore,
    private val passkeys: Passkeys,
    private val totps: TotpStore,
    private val transactions: TransactionTemplate,
) {
    fun remove(
        user: WebUser,
        removal: () -> Unit,
    ) = transactions.executeWithoutResult {
        users.lockForUpdate(user.id)
        if (user.isOwner && factors(user.id) <= 1) {
            throw ConflictException("Owners keep a second factor; add another before removing this one")
        }
        removal()
    }

    private fun factors(id: UUID): Int = passkeys.list(id).size + if (totps.find(id)?.confirmedAt != null) 1 else 0
}
