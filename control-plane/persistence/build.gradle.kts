import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.spring-library")
    `java-test-fixtures`
}

dependencies {
    api(project(":control-plane:domain"))
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // The PostgreSQL container setup, shared with the services' tests.
    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesApi("org.springframework.boot:spring-boot-test")
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
    testFixturesApi("org.testcontainers:testcontainers-postgresql")
}
