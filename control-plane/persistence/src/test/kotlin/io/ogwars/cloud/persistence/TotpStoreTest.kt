package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.store.StoredTotp
import io.ogwars.cloud.domain.store.TotpStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

@PersistenceTest
class TotpStoreTest(
    @Autowired private val totps: TotpStore,
    @Autowired private val users: WebUserStore,
) {
    private val userId = UUID(3, 1)
    private val enrollment = StoredTotp(userId, "k1", byteArrayOf(1, 2, 3))

    @BeforeEach
    fun user() {
        users.create(WebUser(userId, "totp-user", "totp@example.com", null, "viewer", NOW))
    }

    @Test
    fun `an enrollment is confirmed once and then guards against replays`() {
        totps.startEnrollment(enrollment) shouldBe true
        totps.markUsed(userId, step = 5) shouldBe false

        totps.confirm(userId, NOW, step = 10) shouldBe true
        totps.confirm(userId, NOW, step = 11) shouldBe false

        totps.markUsed(userId, step = 10) shouldBe false
        totps.markUsed(userId, step = 11) shouldBe true
        totps.find(userId) shouldBe enrollment.copy(confirmedAt = NOW, lastUsedStep = 11)
    }

    @Test
    fun `a new enrollment replaces an unconfirmed one but never a confirmed one`() {
        totps.startEnrollment(enrollment)
        totps.startEnrollment(enrollment.copy(keyId = "k2")) shouldBe true
        totps.confirm(userId, NOW, step = 1)

        totps.startEnrollment(enrollment.copy(keyId = "k3")) shouldBe false

        totps.find(userId)?.keyId shouldBe "k2"
        totps.delete(userId)
        totps.find(userId) shouldBe null
    }
}
