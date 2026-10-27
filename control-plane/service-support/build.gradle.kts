import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.spring-library")
    `java-test-fixtures`
}

dependencies {
    api(project(":control-plane:domain"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.yaml:snakeyaml")

    testImplementation("org.springframework.boot:spring-boot-test")

    // Shared test helpers of the services, e.g. golden files.
    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesApi(libs.kotest.assertions)
}
