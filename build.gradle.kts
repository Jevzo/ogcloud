plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("plugin.spring") version "2.1.10" apply false
    kotlin("kapt") version "2.1.10" apply false
    id("org.springframework.boot") version "3.5.11" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.gradleup.shadow") version "9.3.2" apply false
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

subprojects {
    group = "io.ogwars.cloud"
    version = readProjectVersion(projectDir)

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
