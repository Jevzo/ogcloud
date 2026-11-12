import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("ogcloud.spotless")
}

group = "io.ogwars.cloud"

kotlin {
    jvmToolchain(PLATFORM_JAVA_VERSION)
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(platform(catalogLibrary("junit-bom")))
    testImplementation(catalogLibrary("junit-jupiter"))
    testImplementation(catalogLibrary("kotest-assertions"))
    testImplementation(catalogLibrary("mockk"))
    testRuntimeOnly(catalogLibrary("junit-platform-launcher"))
}

// Testcontainers images come from one file shared with the Go tests (D-0113), as ogcloud.image.<name>.
val testImages =
    Properties().apply {
        // Builds outside this repository, such as the convention tests, have no such file.
        rootDir
            .resolve("testing/images.properties")
            .takeIf { it.isFile }
            ?.reader()
            ?.use { load(it) }
    }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testImages.forEach { (name, image) -> systemProperty("ogcloud.image.$name", image) }
    // CI keeps only the console, so a failure prints its message and causes, not just the line it threw at.
    testLogging { exceptionFormat = TestExceptionFormat.FULL }
}
