// A control-plane service: the adapters, web and observability the API and the controller share (D-0072, D-0088).
plugins {
    id("ogcloud.spring-service")
}

dependencies {
    "implementation"(project(":control-plane:service-support"))
    "implementation"(project(":control-plane:persistence"))
    "implementation"(project(":control-plane:livestate"))
    "implementation"(project(":control-plane:messaging"))
    "implementation"(project(":control-plane:storage"))
    "implementation"(project(":contracts"))
    "implementation"("org.springframework.boot:spring-boot-starter-webmvc")
    "implementation"("org.springframework.boot:spring-boot-starter-actuator")
    // Traces go out over OTLP; metrics are scraped by Prometheus (D-0072).
    "implementation"("org.springframework.boot:spring-boot-starter-opentelemetry")
    "runtimeOnly"("io.micrometer:micrometer-registry-prometheus")

    "testImplementation"(testFixtures(project(":control-plane:service-support")))
    "testImplementation"(testFixtures(project(":control-plane:persistence")))
    "testImplementation"(testFixtures(project(":control-plane:livestate")))
    "testImplementation"(testFixtures(project(":control-plane:messaging")))
    "testImplementation"("org.springframework.boot:spring-boot-starter-test")
}
