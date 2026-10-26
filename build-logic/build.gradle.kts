plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.allopen.plugin)
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // TestKit projects read the shared catalog, exactly like real modules do.
    systemProperty("ogcloud.catalog", rootDir.resolve("../gradle/libs.versions.toml").absolutePath)
    // A persistent TestKit home keeps downloaded dependencies between runs.
    systemProperty("ogcloud.testkit", rootDir.resolve(".gradle/testkit").absolutePath)
}
