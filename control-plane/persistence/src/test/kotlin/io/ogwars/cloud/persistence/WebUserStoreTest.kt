package io.ogwars.cloud.persistence

import io.ogwars.cloud.domain.store.RoleStore
import io.ogwars.cloud.domain.store.WebUserStore
import io.ogwars.cloud.domain.user.ApiPermission
import io.ogwars.cloud.domain.user.WebUser
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

@PersistenceTest
class WebUserStoreTest(
    @Autowired private val users: WebUserStore,
    @Autowired private val roles: RoleStore,
    @Autowired private val jdbc: JdbcClient,
) {
    private val user = WebUser(UUID(0, 7), "Jevzo", "Jevzo@Example.com", "{bcrypt}hash", "owner", NOW)

    @Test
    fun `users are found by id and by username or email, ignoring case`() {
        users.create(user)

        users.find(user.id) shouldBe user
        users.findByLogin("jevzo") shouldBe user
        users.findByLogin("jevzo@example.com") shouldBe user
        users.findByLogin("someone") shouldBe null
    }

    // One duplicate per test: the failed insert aborts the test's transaction.
    @Test
    fun `usernames are unique regardless of case`() {
        users.create(user)

        shouldThrow<DuplicateKeyException> {
            users.create(user.copy(id = UUID(0, 8), username = "JEVZO", email = "other@example.com"))
        }
    }

    @Test
    fun `emails are unique regardless of case`() {
        users.create(user)

        shouldThrow<DuplicateKeyException> {
            users.create(user.copy(id = UUID(0, 8), username = "other", email = "jevzo@EXAMPLE.com"))
        }
    }

    @Test
    fun `custom role permissions skip entries that left the catalog`() {
        jdbc.sql("insert into roles (id, name) values ('builders', 'Builders')").update()
        jdbc
            .sql(
                "insert into role_permissions (role_id, permission) values ('builders', 'templates:write'), ('builders', 'gone:read')",
            ).update()

        roles.customPermissions("builders") shouldContainExactly setOf(ApiPermission.TEMPLATES_WRITE)
        roles.customPermissions("nobody") shouldBe emptySet()
    }
}
