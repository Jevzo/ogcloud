import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Libraries built on Spring (adapters such as persistence); versions come from the Spring Boot BOM.
plugins {
    id("ogcloud.kotlin-library")
    id("org.jetbrains.kotlin.plugin.spring")
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    // ApplicationContextRunner's context type extends an AssertJ interface; assertions stay Kotest (D-0028).
    testImplementation("org.assertj:assertj-core")
}
