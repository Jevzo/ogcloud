plugins {
    id("ogcloud.control-plane-service")
}

// Committed output of `mise run gen` (contracts/pod-labels.yaml), checked for drift.
sourceSets {
    main {
        kotlin.srcDir("src/generated/kotlin")
    }
}

dependencies {
    implementation(libs.fabric8.kubernetes.client)
    // Renders the load balancer's server-list texts (D-0202); legacy text's nearest colours come from Adventure.
    implementation(project(":contracts:text"))
    implementation(libs.adventure.api)
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")

    testImplementation("org.testcontainers:testcontainers-k3s")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.grpc:grpc-netty")
    testImplementation("io.grpc:grpc-services")
}
