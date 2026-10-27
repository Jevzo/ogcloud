plugins {
    id("ogcloud.spring-service")
}

dependencies {
    implementation(project(":control-plane:service-support"))
    implementation(project(":control-plane:persistence"))
    implementation(project(":control-plane:livestate"))
    implementation(project(":control-plane:messaging"))
    implementation(project(":control-plane:storage"))
    implementation(project(":contracts"))
    implementation(libs.fabric8.kubernetes.client)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    // Traces go out over OTLP; metrics are scraped by Prometheus (D-0072).
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(testFixtures(project(":control-plane:domain")))
    testImplementation(testFixtures(project(":control-plane:service-support")))
    testImplementation(testFixtures(project(":control-plane:persistence")))
    testImplementation(testFixtures(project(":control-plane:livestate")))
    testImplementation(testFixtures(project(":control-plane:messaging")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Kotest's eventually is a suspend function; coroutines already come in through Kotest.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    testImplementation("org.testcontainers:testcontainers-k3s")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.grpc:grpc-netty")
    testImplementation("io.grpc:grpc-services")
}
