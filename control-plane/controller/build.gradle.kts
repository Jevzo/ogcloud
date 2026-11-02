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
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")

    testImplementation("org.testcontainers:testcontainers-k3s")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("io.grpc:grpc-netty")
    testImplementation("io.grpc:grpc-services")
}
