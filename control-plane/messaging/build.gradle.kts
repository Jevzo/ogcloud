import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("ogcloud.spring-library")
    `java-test-fixtures`
}

// Committed output of `mise run gen` (contracts/kafka/topics.yaml), checked for drift.
sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}

dependencies {
    api(project(":contracts"))
    api(project(":control-plane:domain"))
    api("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.springframework.boot:spring-boot-test")

    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesApi("org.springframework.boot:spring-boot-test")
    testFixturesApi("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
