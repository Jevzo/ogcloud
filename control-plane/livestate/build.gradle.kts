plugins {
    id("ogcloud.spring-library")
}

dependencies {
    api(project(":control-plane:domain"))
    implementation(project(":contracts"))
    implementation("io.lettuce:lettuce-core")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
