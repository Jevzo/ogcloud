import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.kotlin-library")
}

// Boots every server image with OgCloud's plugin (D-0139). It needs Docker with buildx and the network, so it runs
// through `mise run check:images` (-Pogcloud.smoke), never as part of `check`.
val smoke: Provider<Boolean> = providers.gradleProperty("ogcloud.smoke").map { true }.orElse(false)

/** The plugin each platform's image is booted with, by the catalog's platform name. */
val pluginProjects =
    mapOf(
        "paper" to ":integrations:paper",
        "pandaspigot" to ":integrations:legacy-paper",
        "velocity" to ":integrations:velocity",
    )
val pluginJars =
    pluginProjects.mapValues { (platform, _) ->
        configurations.create("${platform}Plugin") {
            isCanBeConsumed = false
            isTransitive = false
        }
    }

dependencies {
    testImplementation(project(":contracts"))
    testImplementation(project(":control-plane:domain"))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.testcontainers:testcontainers")
    pluginProjects.forEach { (platform, path) -> add("${platform}Plugin", project(path, "shadowRuntimeElements")) }
}

// The tag every smoke image gets, through the bake file's REGISTRY and VERSION variables.
val smokeRegistry = "ogcloud-smoke"
val smokeVersion = "smoke"

val bakeServerImages =
    tasks.register<Exec>("bakeServerImages") {
        description = "Builds the server images from images/docker-bake.json into the local Docker store"
        val enabled = smoke
        onlyIf { enabled.get() }
        workingDir = rootDir
        environment("REGISTRY", smokeRegistry)
        environment("VERSION", smokeVersion)
        commandLine("docker", "buildx", "bake", "-f", "images/docker-bake.json", "--load")
    }

tasks.test {
    val enabled = smoke
    onlyIf("set -Pogcloud.smoke, as `mise run check:images` does") { enabled.get() }
    dependsOn(bakeServerImages)
    systemProperty("ogcloud.smoke.registry", smokeRegistry)
    systemProperty("ogcloud.smoke.version", smokeVersion)
    systemProperty(
        "ogcloud.smoke.cache",
        layout.buildDirectory
            .dir("downloads")
            .get()
            .asFile.absolutePath,
    )
    val jars: Map<String, FileCollection> = pluginJars
    jars.values.forEach { inputs.files(it) }
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            jars.map { (platform, jar) ->
                "-Dogcloud.smoke.plugin.$platform=${jar.singleFile}"
            }
        },
    )
}
