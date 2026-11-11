// A control-plane service: the adapters, web and observability the API and the controller share (D-0072, D-0088).
plugins {
    id("ogcloud.spring-service")
    // Already applied by spring-service; named here so this file gets the configuration accessors.
    `java-library`
}

dependencies {
    implementation(project(":control-plane:service-support"))
    implementation(project(":control-plane:persistence"))
    implementation(project(":control-plane:livestate"))
    implementation(project(":control-plane:messaging"))
    implementation(project(":control-plane:storage"))
    implementation(project(":contracts"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Spring instantiates Kotlin classes through Kotlin reflection; tests got it transitively, the boot jar did not.
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
    // Traces go out over OTLP; metrics are scraped by Prometheus (D-0072).
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(testFixtures(project(":control-plane:domain")))
    testImplementation(testFixtures(project(":control-plane:service-support")))
    testImplementation(testFixtures(project(":control-plane:persistence")))
    testImplementation(testFixtures(project(":control-plane:livestate")))
    testImplementation(testFixtures(project(":control-plane:messaging")))
    testImplementation(testFixtures(project(":control-plane:storage")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Kotest's eventually is a suspend function; coroutines already come in through Kotest.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
