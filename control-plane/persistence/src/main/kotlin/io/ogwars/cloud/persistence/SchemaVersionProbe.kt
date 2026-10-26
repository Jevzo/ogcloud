package io.ogwars.cloud.persistence

import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** For services that don't run Flyway (the API owns the schema, D-0009): has it reached the version they were built for? */
@Component
class SchemaVersionProbe(
    private val jdbc: JdbcClient,
    resolver: ResourcePatternResolver,
) {
    val requiredVersion: Int =
        resolver
            .getResources(MIGRATIONS)
            .map { versionOf(it.filename ?: error("Migration resource without a file name")) }
            .maxOrNull() ?: error("No migrations found at $MIGRATIONS")

    /** The highest successfully applied version, or null before the first migration. */
    fun appliedVersion(): Int? {
        val historyExists =
            jdbc
                .sql(
                    "select to_regclass('$HISTORY_TABLE') is not null",
                ).query(Boolean::class.java)
                .single()
        if (!historyExists) return null
        return jdbc
            .sql("select version from $HISTORY_TABLE where success and version is not null")
            .query(String::class.java)
            .list()
            .mapNotNull { it?.toInt() }
            .maxOrNull()
    }

    fun isCurrent(): Boolean = (appliedVersion() ?: 0) >= requiredVersion

    private fun versionOf(fileName: String): Int =
        VERSIONED
            .matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?.toInt()
            ?: error("Migration $fileName must be named V<integer>__<description>.sql")

    private companion object {
        const val MIGRATIONS = "classpath*:db/migration/V*__*.sql"
        const val HISTORY_TABLE = "flyway_schema_history"
        val VERSIONED = Regex("""V(\d+)__.+\.sql""")
    }
}
