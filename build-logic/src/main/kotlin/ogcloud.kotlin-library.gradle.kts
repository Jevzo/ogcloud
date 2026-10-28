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
        rootDir.resolve("testing/images.properties").reader().use { load(it) }
    }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testImages.forEach { (name, image) -> systemProperty("ogcloud.image.$name", image) }
}
