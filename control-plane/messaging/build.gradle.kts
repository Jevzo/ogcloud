plugins {
    id("ogcloud.spring-library")
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
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
