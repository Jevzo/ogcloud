plugins {
    id("ogcloud.spring-library")
}

dependencies {
    api(project(":control-plane:domain"))
    implementation(project(":contracts"))
    implementation("io.lettuce:lettuce-core")

    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
