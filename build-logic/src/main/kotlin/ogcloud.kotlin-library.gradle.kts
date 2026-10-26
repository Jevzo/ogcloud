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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
