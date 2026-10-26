import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.spring-library")
    `java-test-fixtures`
}

dependencies {
    api(project(":control-plane:domain"))
    implementation(project(":contracts"))
    api("io.lettuce:lettuce-core")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-test")

    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesApi("org.springframework.boot:spring-boot-test")
    testFixturesApi("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
