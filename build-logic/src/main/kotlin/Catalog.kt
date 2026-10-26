import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

private const val CATALOG_FILE = "gradle/libs.versions.toml"

private val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.catalogVersion(alias: String): String =
    libs
        .findVersion(alias)
        .orElseThrow { GradleException("Version '$alias' is missing from $CATALOG_FILE") }
        .requiredVersion

internal fun Project.catalogLibrary(alias: String): Provider<MinimalExternalModuleDependency> =
    libs
        .findLibrary(alias)
        .orElseThrow { GradleException("Library '$alias' is missing from $CATALOG_FILE") }
