import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.kotlin-library")
}

// Boots every server image with OgCloud's plugin (D-0139). `mise run check:images` builds the images and the
// artifacts in build/images first and passes -Pogcloud.smoke; `check` skips these tests.
val smoke: Provider<Boolean> = providers.gradleProperty("ogcloud.smoke").map { true }.orElse(false)

dependencies {
    testImplementation(project(":contracts"))
    testImplementation(project(":control-plane:domain"))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation("org.testcontainers:testcontainers")
}

tasks.test {
    val enabled = smoke
    onlyIf("set -Pogcloud.smoke, as `mise run check:images` does") { enabled.get() }
    // The images change outside Gradle, so the tests always run when asked for.
    outputs.upToDateWhen { false }
    systemProperty("ogcloud.smoke.artifacts", rootDir.resolve("build/images").absolutePath)
    systemProperty(
        "ogcloud.smoke.cache",
        layout.buildDirectory
            .dir("downloads")
            .get()
            .asFile.absolutePath,
    )
    providers.environmentVariable("REGISTRY").orNull?.let { systemProperty("ogcloud.smoke.registry", it) }
    providers.environmentVariable("VERSION").orNull?.let { systemProperty("ogcloud.smoke.version", it) }
}
