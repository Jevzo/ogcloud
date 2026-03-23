import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.spring") version "2.1.10" apply false
    kotlin("kapt") version "2.1.10" apply false
    id("org.springframework.boot") version "3.5.11" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.gradleup.shadow") version "9.3.2" apply false

    id("org.jlleitschuh.gradle.ktlint") version "14.1.0" apply false
}

fun readProjectVersion(projectDir: File): String {
    val versionFile = projectDir.resolve("VERSION")
    if (!versionFile.isFile) {
        error("Missing VERSION file for ${projectDir.name}: $versionFile")
    }

    val version = versionFile.readText().trim()
    if (version.isEmpty()) {
        error("VERSION file is empty for ${projectDir.name}: $versionFile")
    }

    return version
}

val githubPackagesOwner = providers.gradleProperty("gpr.owner")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY_OWNER"))
    .orElse("Jevzo")

val githubPackagesRepository = providers.gradleProperty("gpr.repo")
    .orElse(providers.environmentVariable("GITHUB_REPOSITORY").map { it.substringAfter('/') })
    .orElse("ogcloud")

val githubPackagesUsername = providers.gradleProperty("gpr.user")
    .orElse(providers.environmentVariable("GITHUB_ACTOR"))

val githubPackagesToken = providers.gradleProperty("gpr.key")
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))

fun Project.configureGitHubPackagesPublication() {
    apply(plugin = "maven-publish")

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
        }

        val publishedGroup = project.group.toString()
        val publishedArtifact = project.name
        val publishedVersion = project.version.toString()

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri(
                        "https://maven.pkg.github.com/${githubPackagesOwner.get().lowercase()}/${githubPackagesRepository.get()}",
                    )
                    credentials {
                        username = githubPackagesUsername.orNull
                        password = githubPackagesToken.orNull
                    }
                }
            }

            publications {
                register<MavenPublication>("gpr") {
                    groupId = publishedGroup
                    artifactId = publishedArtifact
                    version = publishedVersion

                    if (pluginManager.hasPlugin("com.gradleup.shadow")) {
                        artifact(tasks.named("shadowJar"))
                        artifact(tasks.named("sourcesJar"))
                    } else {
                        from(components["java"])
                    }
                }
            }
        }
    }
}

subprojects {
    group = "io.ogwars.cloud"
    version = readProjectVersion(projectDir)

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<KtlintExtension> {
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("ktlintCheck")
    }
}

listOf(
    project(":ogcloud-common"),
    project(":ogcloud-legacy-paper-plugin"),
    project(":ogcloud-paper-plugin"),
    project(":ogcloud-velocity-plugin"),
).forEach { project ->
    project.configureGitHubPackagesPublication()
}
