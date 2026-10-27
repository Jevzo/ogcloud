import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.spring-library")
    `java-test-fixtures`
}

dependencies {
    api(project(":control-plane:domain"))
    implementation(platform(libs.aws.sdk.bom))
    implementation("software.amazon.awssdk:s3")
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesImplementation(platform(libs.aws.sdk.bom))
    testFixturesImplementation("software.amazon.awssdk:s3")
    testFixturesApi("org.springframework.boot:spring-boot-test")
    testFixturesApi("org.testcontainers:testcontainers")
}
